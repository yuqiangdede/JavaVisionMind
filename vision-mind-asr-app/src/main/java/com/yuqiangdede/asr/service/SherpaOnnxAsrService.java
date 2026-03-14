package com.yuqiangdede.asr.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class SherpaOnnxAsrService {

    private final AsrPathResolver pathResolver;

    private URLClassLoader classLoader;
    private Object recognizer;
    private String recognizerSignature;

    @PostConstruct
    public synchronized void warmup() {
        try {
            validateModelFiles();
            ensureRecognizer(List.of());
            log.info("Sherpa ONNX ASR warmup completed: model={}", pathResolver.getModelDir());
        } catch (Exception e) {
            log.warn("Sherpa ONNX ASR warmup failed: {}", e.getMessage());
        }
    }

    public synchronized String transcribe(float[] samples, List<String> hotwords) {
        validateModelFiles();
        List<String> normalizedHotwords = normalizeHotwords(hotwords);
        ensureRecognizer(normalizedHotwords);
        try {
            Object stream = recognizer.getClass().getMethod("createStream").invoke(recognizer);
            invokeAcceptWaveform(stream, 16000, samples);
            invokeIfPresent(stream, "inputFinished");
            if (isStreamingModel()) {
                decodeOnlineStream(stream);
            } else {
                Class<?> streamClass = loadClass("com.k2fsa.sherpa.onnx.OfflineStream");
                recognizer.getClass().getMethod("decode", streamClass).invoke(recognizer, stream);
            }
            Object result = resolveResult(stream);
            Object text = result.getClass().getMethod("getText").invoke(result);
            invokeIfPresent(stream, "close");
            invokeIfPresent(stream, "release");
            return text == null ? "" : text.toString().trim();
        } catch (Exception e) {
            throw new IllegalStateException("Sherpa ONNX 识别失败: " + e.getMessage(), e);
        }
    }

    public synchronized boolean isReady() {
        try {
            validateModelFiles();
            return Files.isRegularFile(pathResolver.getRuntimeJavaJar()) && Files.isRegularFile(pathResolver.getRuntimeNativeJar());
        } catch (Exception e) {
            return false;
        }
    }

    public synchronized String runtimeMessage() {
        try {
            validateModelFiles();
            if (!Files.isRegularFile(pathResolver.getRuntimeJavaJar())) {
                return "缺少 sherpa-onnx Java API jar: " + pathResolver.getRuntimeJavaJar();
            }
            if (!Files.isRegularFile(pathResolver.getRuntimeNativeJar())) {
                return "缺少 sherpa-onnx native jar: " + pathResolver.getRuntimeNativeJar();
            }
            return "ready";
        } catch (Exception e) {
            return e.getMessage();
        }
    }

    private void ensureRecognizer(List<String> hotwords) {
        String signature = String.join("|", hotwords);
        if (recognizer != null && signature.equals(recognizerSignature)) {
            return;
        }
        closeRecognizer();
        recognizer = buildRecognizer(hotwords);
        recognizerSignature = signature;
    }

    private Object buildRecognizer(List<String> hotwords) {
        try {
            Path hotwordsFile = buildHotwordsFile(hotwords);
            Object modelConfig = buildModelConfig();
            Object recognizerConfig = buildRecognizerConfig(modelConfig, hotwordsFile);

            Class<?> recognizerClass = isStreamingModel()
                    ? loadClass("com.k2fsa.sherpa.onnx.OnlineRecognizer")
                    : loadClass("com.k2fsa.sherpa.onnx.OfflineRecognizer");
            Class<?> configClass = isStreamingModel()
                    ? loadClass("com.k2fsa.sherpa.onnx.OnlineRecognizerConfig")
                    : loadClass("com.k2fsa.sherpa.onnx.OfflineRecognizerConfig");
            Constructor<?> constructor = recognizerClass.getConstructor(configClass);
            Object builtRecognizer = constructor.newInstance(recognizerConfig);
            log.info("Sherpa ONNX recognizer initialized: model={} hotwords={}", pathResolver.getModelDir(), hotwords.size());
            return builtRecognizer;
        } catch (Exception e) {
            throw new IllegalStateException("初始化 Sherpa ONNX 识别器失败: " + e.getMessage(), e);
        }
    }

    private Object buildTransducerConfig() throws Exception {
        Class<?> clazz = loadClass("com.k2fsa.sherpa.onnx.OfflineTransducerModelConfig");
        Object builder = clazz.getMethod("builder").invoke(null);
        invokeSetter(builder, "setEncoder", selectModelFile("encoder").toString());
        invokeSetter(builder, "setDecoder", selectModelFile("decoder").toString());
        invokeSetter(builder, "setJoiner", selectModelFile("joiner").toString());
        return builder.getClass().getMethod("build").invoke(builder);
    }

    private Object buildModelConfig() throws Exception {
        if (isStreamingModel()) {
            return buildOnlineModelConfig();
        }
        Class<?> clazz = loadClass("com.k2fsa.sherpa.onnx.OfflineModelConfig");
        Object builder = clazz.getMethod("builder").invoke(null);
        invokeSetter(builder, "setTransducer", buildTransducerConfig());
        invokeSetter(builder, "setTokens", pathResolver.getModelDir().resolve("tokens.txt").toString());
        invokeSetter(builder, "setNumThreads", pathResolver.getNumThreads());
        invokeSetter(builder, "setProvider", pathResolver.getProvider());
        invokeSetter(builder, "setDebug", false);
        return builder.getClass().getMethod("build").invoke(builder);
    }

    private Object buildOnlineTransducerConfig() throws Exception {
        Class<?> clazz = loadClass("com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig");
        Object builder = clazz.getMethod("builder").invoke(null);
        invokeSetter(builder, "setEncoder", selectModelFile("encoder").toString());
        invokeSetter(builder, "setDecoder", selectModelFile("decoder").toString());
        invokeSetter(builder, "setJoiner", selectModelFile("joiner").toString());
        return builder.getClass().getMethod("build").invoke(builder);
    }

    private Object buildOnlineModelConfig() throws Exception {
        Class<?> clazz = loadClass("com.k2fsa.sherpa.onnx.OnlineModelConfig");
        Object builder = clazz.getMethod("builder").invoke(null);
        invokeSetter(builder, "setTransducer", buildOnlineTransducerConfig());
        invokeSetter(builder, "setTokens", pathResolver.getModelDir().resolve("tokens.txt").toString());
        invokeSetter(builder, "setNumThreads", pathResolver.getNumThreads());
        invokeSetter(builder, "setProvider", pathResolver.getProvider());
        invokeSetter(builder, "setDebug", false);
        return builder.getClass().getMethod("build").invoke(builder);
    }

    private Object buildRecognizerConfig(Object modelConfig, Path hotwordsFile) throws Exception {
        if (isStreamingModel()) {
            return buildOnlineRecognizerConfig(modelConfig, hotwordsFile);
        }
        Class<?> clazz = loadClass("com.k2fsa.sherpa.onnx.OfflineRecognizerConfig");
        Object builder = clazz.getMethod("builder").invoke(null);
        invokeSetter(builder, "setOfflineModelConfig", modelConfig);
        invokeSetter(builder, "setDecodingMethod", pathResolver.getDecodingMethod());
        invokeSetter(builder, "setMaxActivePaths", 4);
        invokeSetter(builder, "setBlankPenalty", 0.0f);
        if (hotwordsFile != null) {
            invokeSetter(builder, "setHotwordsFile", hotwordsFile.toString());
            invokeSetter(builder, "setHotwordsScore", pathResolver.getHotwordsScore());
        }
        return builder.getClass().getMethod("build").invoke(builder);
    }

    private Object buildOnlineRecognizerConfig(Object modelConfig, Path hotwordsFile) throws Exception {
        Class<?> clazz = loadClass("com.k2fsa.sherpa.onnx.OnlineRecognizerConfig");
        Object builder = clazz.getMethod("builder").invoke(null);
        invokeSetter(builder, "setOnlineModelConfig", modelConfig);
        invokeSetter(builder, "setDecodingMethod", pathResolver.getDecodingMethod());
        invokeSetter(builder, "setMaxActivePaths", 4);
        invokeSetter(builder, "setBlankPenalty", 0.0f);
        if (hotwordsFile != null) {
            invokeSetter(builder, "setHotwordsFile", hotwordsFile.toString());
            invokeSetter(builder, "setHotwordsScore", pathResolver.getHotwordsScore());
        }
        return builder.getClass().getMethod("build").invoke(builder);
    }

    private Path buildHotwordsFile(List<String> hotwords) {
        try {
            if (hotwords.isEmpty()) {
                return null;
            }
            pathResolver.ensureMutableDirectories();
            String signature = Integer.toHexString(String.join("\n", hotwords).hashCode());
            Path file = pathResolver.getHotwordsDir().resolve("hotwords-" + signature + ".txt");
            if (!Files.exists(file)) {
                Files.write(file, hotwords);
            }
            return file;
        } catch (Exception e) {
            throw new IllegalStateException("生成热词文件失败", e);
        }
    }

    private List<String> normalizeHotwords(List<String> hotwords) {
        Set<String> values = new LinkedHashSet<>();
        if (hotwords != null) {
            for (String hotword : hotwords) {
                String normalized = hotword == null ? "" : hotword.trim();
                if (!normalized.isEmpty()) {
                    values.add(normalized);
                }
            }
        }
        return List.copyOf(values);
    }

    private void validateModelFiles() {
        Path modelDir = pathResolver.getModelDir();
        if (!Files.isDirectory(modelDir)) {
            throw new IllegalStateException("ASR 模型目录不存在: " + modelDir);
        }
        requireFile(modelDir.resolve("tokens.txt"));
        requireFile(selectModelFile("encoder"));
        requireFile(selectModelFile("decoder"));
        requireFile(selectModelFile("joiner"));
    }

    Path selectModelFile(String prefix) {
        try (var stream = Files.list(pathResolver.getModelDir())) {
            boolean preferInt8 = !"decoder".equals(prefix);
            return stream
                    .filter(Files::isRegularFile)
                    .filter(path -> matchesModelPrefix(path, prefix))
                    .filter(path -> path.getFileName().toString().endsWith(".onnx"))
                    .sorted((left, right) -> compareModelPath(left, right, preferInt8))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("缺少模型文件: " + prefix + "-*.onnx"));
        } catch (Exception e) {
            if (e instanceof IllegalStateException) {
                throw (IllegalStateException) e;
            }
            throw new IllegalStateException("解析模型文件失败: " + prefix, e);
        }
    }

    private int compareModelPath(Path left, Path right, boolean preferInt8) {
        boolean leftInt8 = left.getFileName().toString().contains(".int8.");
        boolean rightInt8 = right.getFileName().toString().contains(".int8.");
        boolean leftFp16 = left.getFileName().toString().contains(".fp16.");
        boolean rightFp16 = right.getFileName().toString().contains(".fp16.");
        if (leftInt8 != rightInt8) {
            if (preferInt8) {
                return leftInt8 ? -1 : 1;
            }
            return leftInt8 ? 1 : -1;
        }
        if (leftFp16 != rightFp16) {
            if (preferInt8) {
                return leftFp16 ? 1 : -1;
            }
            return leftFp16 ? -1 : 1;
        }
        return left.getFileName().toString().compareTo(right.getFileName().toString());
    }

    private boolean matchesModelPrefix(Path path, String prefix) {
        String fileName = path.getFileName().toString();
        return fileName.startsWith(prefix + "-") || fileName.startsWith(prefix + ".");
    }

    private void requireFile(Path path) {
        if (!Files.isRegularFile(path)) {
            throw new IllegalStateException("缺少文件: " + path);
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
            throw new IllegalStateException("缺少 sherpa-onnx 运行时 jar，请放入 resource/lib/sherpa-onnx");
        }
        classLoader = new URLClassLoader(
                new URL[]{javaJar.toUri().toURL(), nativeJar.toUri().toURL()},
                getClass().getClassLoader()
        );
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

    private Object resolveResult(Object stream) throws Exception {
        for (Method method : recognizer.getClass().getMethods()) {
            if (!"getResult".equals(method.getName()) || method.getParameterCount() != 1) {
                continue;
            }
            return method.invoke(recognizer, stream);
        }
        return stream.getClass().getMethod("getResult").invoke(stream);
    }

    private void invokeAcceptWaveform(Object stream, int sampleRate, float[] samples) throws Exception {
        for (Method method : stream.getClass().getMethods()) {
            if (!"acceptWaveform".equals(method.getName())) {
                continue;
            }
            Class<?>[] parameterTypes = method.getParameterTypes();
            if (parameterTypes.length == 2
                    && isSampleRateType(parameterTypes[0])
                    && parameterTypes[1] == float[].class) {
                method.invoke(stream, adaptValue(parameterTypes[0], sampleRate), samples);
                return;
            }
            if (parameterTypes.length == 2
                    && parameterTypes[0] == float[].class
                    && isSampleRateType(parameterTypes[1])) {
                method.invoke(stream, samples, adaptValue(parameterTypes[1], sampleRate));
                return;
            }
            if (parameterTypes.length == 1 && parameterTypes[0] == float[].class) {
                method.invoke(stream, new Object[]{samples});
                return;
            }
        }
        throw new NoSuchMethodException(stream.getClass().getName() + ".acceptWaveform");
    }

    private boolean isSampleRateType(Class<?> type) {
        return type == int.class
                || type == Integer.class
                || type == float.class
                || type == Float.class
                || type == double.class
                || type == Double.class;
    }

    private void decodeOnlineStream(Object stream) throws Exception {
        Method isReadyMethod = findSingleArgMethod(recognizer.getClass(), "isReady", stream.getClass());
        Method decodeMethod = findSingleArgMethod(recognizer.getClass(), "decode", stream.getClass());
        if (isReadyMethod == null || decodeMethod == null) {
            throw new NoSuchMethodException(recognizer.getClass().getName() + ".isReady/decode");
        }
        while (Boolean.TRUE.equals(isReadyMethod.invoke(recognizer, stream))) {
            decodeMethod.invoke(recognizer, stream);
        }
    }

    private Method findSingleArgMethod(Class<?> clazz, String name, Class<?> argType) {
        for (Method method : clazz.getMethods()) {
            if (!name.equals(method.getName()) || method.getParameterCount() != 1) {
                continue;
            }
            Class<?> parameterType = method.getParameterTypes()[0];
            if (parameterType.isAssignableFrom(argType) || argType.isAssignableFrom(parameterType)) {
                return method;
            }
        }
        return null;
    }

    private boolean isStreamingModel() {
        String modelName = pathResolver.getModelDir().getFileName() == null
                ? ""
                : pathResolver.getModelDir().getFileName().toString().toLowerCase();
        return modelName.contains("streaming");
    }

    private Object adaptValue(Class<?> targetType, Object value) {
        if (value == null || targetType.isAssignableFrom(value.getClass())) {
            return value;
        }
        if ((targetType == int.class || targetType == Integer.class) && value instanceof Number number) {
            return number.intValue();
        }
        if ((targetType == float.class || targetType == Float.class) && value instanceof Number number) {
            return number.floatValue();
        }
        if ((targetType == double.class || targetType == Double.class) && value instanceof Number number) {
            return number.doubleValue();
        }
        if ((targetType == boolean.class || targetType == Boolean.class) && value instanceof Boolean bool) {
            return bool;
        }
        return value;
    }

    private void invokeIfPresent(Object target, String methodName) {
        try {
            target.getClass().getMethod(methodName).invoke(target);
        } catch (Exception ignored) {
        }
    }

    private void closeRecognizer() {
        if (recognizer == null) {
            return;
        }
        invokeIfPresent(recognizer, "close");
        invokeIfPresent(recognizer, "release");
        recognizer = null;
        recognizerSignature = null;
    }

    @PreDestroy
    public synchronized void destroy() {
        closeRecognizer();
        if (classLoader != null) {
            try {
                classLoader.close();
            } catch (Exception ignored) {
            }
        }
    }
}
