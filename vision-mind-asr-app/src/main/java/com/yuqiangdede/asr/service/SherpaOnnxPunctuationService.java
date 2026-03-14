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

@Service
@RequiredArgsConstructor
@Slf4j
public class SherpaOnnxPunctuationService {

    private final AsrPathResolver pathResolver;

    private URLClassLoader classLoader;
    private Object punctuation;

    @PostConstruct
    public synchronized void warmup() {
        try {
            ensurePunctuation();
            log.info("Sherpa ONNX punctuation warmup completed: model={}", pathResolver.getPunctuationModelDir());
        } catch (Exception e) {
            log.warn("Sherpa ONNX punctuation warmup failed: {}", e.getMessage());
        }
    }

    public synchronized String addPunctuation(String text) {
        String current = text == null ? "" : text.trim();
        if (current.isEmpty()) {
            return current;
        }
        ensurePunctuation();
        try {
            Object result = punctuation.getClass().getMethod("addPunctuation", String.class).invoke(punctuation, current);
            return result == null ? current : result.toString().trim();
        } catch (Exception e) {
            throw new IllegalStateException("标点恢复失败: " + e.getMessage(), e);
        }
    }

    private void ensurePunctuation() {
        if (punctuation != null) {
            return;
        }
        try {
            Path modelDir = pathResolver.getPunctuationModelDir();
            Path modelFile = modelDir.resolve("model.int8.onnx");
            if (!Files.isRegularFile(modelFile)) {
                throw new IllegalStateException("缺少标点模型文件: " + modelFile);
            }

            Class<?> modelConfigClass = loadClass("com.k2fsa.sherpa.onnx.OfflinePunctuationModelConfig");
            Object modelBuilder = modelConfigClass.getMethod("builder").invoke(null);
            invokeSetter(modelBuilder, "setCtTransformer", modelFile.toString());
            invokeSetter(modelBuilder, "setNumThreads", pathResolver.getNumThreads());
            invokeSetter(modelBuilder, "setProvider", pathResolver.getProvider());
            invokeSetter(modelBuilder, "setDebug", false);
            Object modelConfig = modelBuilder.getClass().getMethod("build").invoke(modelBuilder);

            Class<?> configClass = loadClass("com.k2fsa.sherpa.onnx.OfflinePunctuationConfig");
            Object configBuilder = configClass.getMethod("builder").invoke(null);
            invokeSetter(configBuilder, "setModel", modelConfig);
            Object config = configBuilder.getClass().getMethod("build").invoke(configBuilder);

            Class<?> punctuationClass = loadClass("com.k2fsa.sherpa.onnx.OfflinePunctuation");
            Constructor<?> constructor = punctuationClass.getConstructor(configClass);
            punctuation = constructor.newInstance(config);
        } catch (Exception e) {
            throw new IllegalStateException("初始化标点恢复失败: " + e.getMessage(), e);
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

    @PreDestroy
    public synchronized void destroy() {
        if (punctuation != null) {
            try {
                punctuation.getClass().getMethod("release").invoke(punctuation);
            } catch (Exception ignored) {
            }
            punctuation = null;
        }
        if (classLoader != null) {
            try {
                classLoader.close();
            } catch (Exception ignored) {
            }
        }
    }
}
