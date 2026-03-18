package com.yuqiangdede.tts.service;

import com.yuqiangdede.tts.dto.output.TtsHealthResponse;
import com.yuqiangdede.tts.dto.output.TtsVoiceItem;
import com.yuqiangdede.tts.dto.output.TtsVoicesResponse;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

@Service
@Slf4j
public class SherpaOnnxTtsService {

    private final TtsPathResolver pathResolver;
    private URLClassLoader classLoader;
    private ModelRuntime runtime;

    public SherpaOnnxTtsService(TtsPathResolver pathResolver) {
        this.pathResolver = pathResolver;
    }

    @PostConstruct
    public synchronized void warmup() {
        try {
            ensureRuntime();
            log.info("Sherpa ONNX TTS warmup completed: model={}", getModelId());
        } catch (Exception e) {
            log.warn("Sherpa ONNX TTS warmup failed: {}", e.getMessage());
        }
    }

    public String getModelId() {
        return pathResolver.getDefaultModel();
    }

    public synchronized TtsHealthResponse health() {
        Path modelDir = getModelDir();
        try {
            ModelRuntime current = ensureRuntime();
            return new TtsHealthResponse(true, "ready", getModelId(), modelDir.toString(),
                    pathResolver.getRuntimeJavaJar().toString(), pathResolver.getRuntimeNativeJar().toString(),
                    current.numSpeakers, current.sampleRate, resolveDefaultVoice(current.numSpeakers));
        } catch (Exception e) {
            return new TtsHealthResponse(false, e.getMessage(), getModelId(), modelDir.toString(),
                    pathResolver.getRuntimeJavaJar().toString(), pathResolver.getRuntimeNativeJar().toString(),
                    0, 0, pathResolver.getDefaultSpeakerId());
        }
    }

    public synchronized TtsVoicesResponse voices() {
        ModelRuntime current = ensureRuntime();
        int defaultVoice = resolveDefaultVoice(current.numSpeakers);
        List<TtsVoiceItem> items = new ArrayList<>();
        for (int i = 0; i < current.numSpeakers; i++) {
            items.add(new TtsVoiceItem(i, resolveVoiceName(i), i == defaultVoice));
        }
        return new TtsVoicesResponse(getModelId(), current.sampleRate, defaultVoice, items);
    }

    public synchronized TtsSynthesisResult synthesize(String text, Integer voice, Float speed) {
        validateText(text);
        ModelRuntime current = ensureRuntime();
        int targetVoice = voice == null ? resolveDefaultVoice(current.numSpeakers) : voice;
        if (targetVoice < 0 || targetVoice >= current.numSpeakers) {
            throw new IllegalArgumentException("Invalid voice: " + targetVoice + ", valid range is 0-" + (current.numSpeakers - 1));
        }

        float targetSpeed = speed == null ? pathResolver.getDefaultSpeed() : speed;
        if (targetSpeed <= 0.1f || targetSpeed > 3.0f) {
            throw new IllegalArgumentException("speed must be in (0.1, 3.0]");
        }

        try {
            Object generatedAudio = invokeGenerate(current.offlineTts, text.trim(), targetVoice, targetSpeed);
            float[] samples = (float[]) generatedAudio.getClass().getMethod("getSamples").invoke(generatedAudio);
            int sampleRate = (Integer) generatedAudio.getClass().getMethod("getSampleRate").invoke(generatedAudio);
            return new TtsSynthesisResult(text.trim(), targetVoice, sampleRate, samples);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Sherpa ONNX TTS failed: " + e.getMessage(), e);
        }
    }

    private Object invokeGenerate(Object offlineTts, String text, int voice, float speed) throws Exception {
        for (Method method : offlineTts.getClass().getMethods()) {
            if (!"generate".equals(method.getName())) {
                continue;
            }
            Class<?>[] parameterTypes = method.getParameterTypes();
            if (parameterTypes.length == 3 && parameterTypes[0] == String.class && isInt(parameterTypes[1]) && isFloat(parameterTypes[2])) {
                return method.invoke(offlineTts, text, voice, speed);
            }
        }

        Class<?> generationConfigClass = loadClass("com.k2fsa.sherpa.onnx.GenerationConfig");
        Object generationConfig = generationConfigClass.getConstructor().newInstance();
        generationConfigClass.getMethod("setSid", int.class).invoke(generationConfig, voice);
        generationConfigClass.getMethod("setSpeed", float.class).invoke(generationConfig, speed);
        generationConfigClass.getMethod("setSilenceScale", float.class).invoke(generationConfig, pathResolver.getSilenceScale());

        Method method = offlineTts.getClass().getMethod(
                "generateWithConfigAndCallback", String.class, generationConfigClass, java.util.function.Consumer.class);
        return method.invoke(offlineTts, text, generationConfig, null);
    }

    private void validateText(String text) {
        String normalized = text == null ? "" : text.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("text must not be empty");
        }
        if (normalized.length() > pathResolver.getMaxInputLength()) {
            throw new IllegalArgumentException("text is too long, max length is " + pathResolver.getMaxInputLength());
        }
    }

    private int resolveDefaultVoice(int numSpeakers) {
        int configured = pathResolver.getDefaultSpeakerId();
        if (numSpeakers <= 0) {
            return 0;
        }
        if (configured < 0 || configured >= numSpeakers) {
            return 0;
        }
        return configured;
    }

    private String resolveVoiceName(int speakerId) {
        switch (speakerId) {
            case 0:
                return "女1";
            case 1:
                return "男1";
            case 2:
                return "女2";
            case 3:
                return "男2";
            case 4:
                return "男3";
            default:
                return "speaker-" + speakerId;
        }
    }

    private ModelRuntime ensureRuntime() {
        if (runtime != null) {
            return runtime;
        }

        try {
            Path modelDir = getModelDir();
            validateModelFiles(modelDir);
            Object offlineTtsConfig = buildTtsConfig(modelDir);
            Class<?> offlineTtsClass = loadClass("com.k2fsa.sherpa.onnx.OfflineTts");
            Constructor<?> constructor = offlineTtsClass.getConstructor(loadClass("com.k2fsa.sherpa.onnx.OfflineTtsConfig"));
            Object offlineTts = constructor.newInstance(offlineTtsConfig);
            int sampleRate = (Integer) offlineTtsClass.getMethod("getSampleRate").invoke(offlineTts);
            int numSpeakers = (Integer) offlineTtsClass.getMethod("getNumSpeakers").invoke(offlineTts);
            runtime = new ModelRuntime(offlineTts, sampleRate, numSpeakers);
            log.info("Sherpa ONNX TTS initialized: model={} sampleRate={} numSpeakers={}", getModelId(), sampleRate, numSpeakers);
            return runtime;
        } catch (Exception e) {
            closeRuntime();
            throw new IllegalStateException("Failed to initialize Sherpa ONNX TTS: " + e.getMessage(), e);
        }
    }

    private Path getModelDir() {
        return pathResolver.getModelDir(getModelId());
    }

    private Object buildTtsConfig(Path modelDir) throws Exception {
        Object vitsConfig = buildVitsConfig(modelDir);
        Class<?> modelConfigClass = loadClass("com.k2fsa.sherpa.onnx.OfflineTtsModelConfig");
        Object modelBuilder = modelConfigClass.getMethod("builder").invoke(null);
        invokeSetter(modelBuilder, "setVits", vitsConfig);
        invokeSetter(modelBuilder, "setNumThreads", pathResolver.getNumThreads());
        invokeSetter(modelBuilder, "setProvider", pathResolver.getProvider());
        invokeSetter(modelBuilder, "setDebug", false);
        Object modelConfig = modelBuilder.getClass().getMethod("build").invoke(modelBuilder);

        Class<?> offlineTtsConfigClass = loadClass("com.k2fsa.sherpa.onnx.OfflineTtsConfig");
        Object configBuilder = offlineTtsConfigClass.getMethod("builder").invoke(null);
        invokeSetter(configBuilder, "setModel", modelConfig);
        invokeSetter(configBuilder, "setRuleFsts", pathResolver.getRuleFsts(modelDir));
        invokeSetter(configBuilder, "setRuleFars", "");
        invokeSetter(configBuilder, "setMaxNumSentences", 1);
        invokeSetter(configBuilder, "setSilenceScale", pathResolver.getSilenceScale());
        return configBuilder.getClass().getMethod("build").invoke(configBuilder);
    }

    private Object buildVitsConfig(Path modelDir) throws Exception {
        Class<?> vitsConfigClass = loadClass("com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig");
        Object builder = vitsConfigClass.getMethod("builder").invoke(null);
        invokeSetter(builder, "setModel", selectModelFile(modelDir).toString());
        invokeSetter(builder, "setLexicon", modelDir.resolve("lexicon.txt").toString());
        invokeSetter(builder, "setTokens", modelDir.resolve("tokens.txt").toString());
        invokeSetter(builder, "setLengthScale", pathResolver.getLengthScale());
        invokeSetter(builder, "setNoiseScale", pathResolver.getNoiseScale());
        invokeSetter(builder, "setNoiseScaleW", pathResolver.getNoiseScaleW());
        return builder.getClass().getMethod("build").invoke(builder);
    }

    private void validateModelFiles(Path modelDir) {
        if (!Files.isDirectory(modelDir)) {
            throw new IllegalStateException("TTS model directory does not exist: " + modelDir);
        }
        requireFile(pathResolver.getRuntimeJavaJar());
        requireFile(pathResolver.getRuntimeNativeJar());
        requireFile(selectModelFile(modelDir));
        requireFile(modelDir.resolve("lexicon.txt"));
        requireFile(modelDir.resolve("tokens.txt"));
        requireFile(modelDir.resolve("phone.fst"));
        requireFile(modelDir.resolve("date.fst"));
        requireFile(modelDir.resolve("number.fst"));
    }

    private Path selectModelFile(Path modelDir) {
        try (java.util.stream.Stream<Path> stream = Files.list(modelDir)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".onnx"))
                    .filter(this::isUsableModelFile)
                    .sorted(Comparator.comparingInt(this::modelFilePriority)
                            .thenComparing(path -> path.getFileName().toString()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("Missing usable ONNX model in " + modelDir));
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to resolve model file in " + modelDir, e);
        }
    }

    private int modelFilePriority(Path path) {
        String fileName = path.getFileName().toString();
        if ("model.onnx".equals(fileName)) {
            return 0;
        }
        if (fileName.endsWith(".int8.onnx")) {
            return 2;
        }
        return 1;
    }

    private boolean isUsableModelFile(Path path) {
        if (!Files.isRegularFile(path)) {
            return false;
        }
        try {
            long size = Files.size(path);
            if (size <= 1024) {
                List<String> lines = Files.readAllLines(path);
                return lines.stream().noneMatch(line -> line.contains("git-lfs.github.com/spec/v1"));
            }

            byte[] prefix = new byte[64];
            int inspectLength;
            try (InputStream input = Files.newInputStream(path)) {
                inspectLength = input.read(prefix);
            }
            if (inspectLength <= 0) {
                return false;
            }
            String header = new String(Arrays.copyOf(prefix, inspectLength), java.nio.charset.StandardCharsets.US_ASCII);
            return !header.contains("git-lfs.github.com/spec/v1");
        } catch (Exception e) {
            throw new IllegalStateException("Failed to inspect model file: " + path, e);
        }
    }

    private Class<?> loadClass(String className) throws Exception {
        return Class.forName(className, true, ensureClassLoader());
    }

    private URLClassLoader ensureClassLoader() throws Exception {
        if (classLoader != null) {
            return classLoader;
        }
        Path javaJar = pathResolver.getRuntimeJavaJar();
        Path nativeJar = pathResolver.getRuntimeNativeJar();
        if (!Files.isRegularFile(javaJar) || !Files.isRegularFile(nativeJar)) {
            throw new IllegalStateException("Missing sherpa-onnx runtime jars.\nRun the download script first.");
        }
        classLoader = new URLClassLoader(
                new URL[]{javaJar.toUri().toURL(), nativeJar.toUri().toURL()},
                getClass().getClassLoader());
        return classLoader;
    }

    private void invokeSetter(Object target, String methodName, Object value) throws Exception {
        for (Method method : target.getClass().getMethods()) {
            if (!method.getName().equals(methodName) || method.getParameterCount() != 1) {
                continue;
            }
            method.invoke(target, adaptValue(method.getParameterTypes()[0], value));
            return;
        }
        throw new NoSuchMethodException(target.getClass().getName() + "#" + methodName);
    }

    private Object adaptValue(Class<?> targetType, Object value) {
        if (value == null || targetType.isAssignableFrom(value.getClass())) {
            return value;
        }
        if ((targetType == int.class || targetType == Integer.class) && value instanceof Number) {
            return ((Number) value).intValue();
        }
        if ((targetType == float.class || targetType == Float.class) && value instanceof Number) {
            return ((Number) value).floatValue();
        }
        if ((targetType == double.class || targetType == Double.class) && value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return value;
    }

    private boolean isInt(Class<?> type) {
        return type == int.class || type == Integer.class;
    }

    private boolean isFloat(Class<?> type) {
        return type == float.class || type == Float.class;
    }

    private void requireFile(Path path) {
        if (!Files.isRegularFile(path)) {
            throw new IllegalStateException("Missing file: " + path);
        }
    }

    private void closeRuntime() {
        if (runtime == null) {
            return;
        }
        try {
            runtime.offlineTts.getClass().getMethod("release").invoke(runtime.offlineTts);
        } catch (Exception ignored) {
        }
        runtime = null;
    }

    @PreDestroy
    public synchronized void destroy() {
        closeRuntime();
        if (classLoader != null) {
            try {
                classLoader.close();
            } catch (Exception ignored) {
            }
        }
    }

    private static final class ModelRuntime {

        private final Object offlineTts;
        private final int sampleRate;
        private final int numSpeakers;

        private ModelRuntime(Object offlineTts, int sampleRate, int numSpeakers) {
            this.offlineTts = offlineTts;
            this.sampleRate = sampleRate;
            this.numSpeakers = numSpeakers;
        }
    }
}
