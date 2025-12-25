package com.yuqiangdede.tbir.config;

import com.yuqiangdede.common.vector.VectorStoreMode;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
public class Constant {

    // Native library locations
    public static final String OPENCV_DLL_PATH;
    public static final String OPENCV_SO_PATH;

    public static final String IMG_ONNX;
    public static final String TEXT_ONNX;
    public static final String CLIP_TOKENIZER;
    public static final int VISION_IMAGE_SIZE;
    public static final String VISION_IMAGE_INPUT_NAME;
    public static final String VISION_TEXT_INPUT_IDS_NAME;
    public static final String VISION_TEXT_ATTENTION_MASK_NAME;
    public static final float[] VISION_IMAGE_MEAN;
    public static final float[] VISION_IMAGE_STD;

    public static final String LUCENE_PATH;
    public static final VectorStoreMode VECTOR_STORE_MODE;

    public static final String ES_URIS;
    public static final String ES_USERNAME;
    public static final String ES_PASSWORD;
    public static final String ES_API_KEY;
    public static final String ES_TBIR_INDEX;

    public static final Boolean OPEN_DETECT;

    public static final int MIN_SIZE;
    public static final int MAX_SIZE;

    public static final int KEY_NUM;

    // Augment configuration
    public static final Set<String> AUGMENT_TYPES;

    // Vectorization configuration
    public static final Set<String> DETECT_TYPES;

    static {
        Properties properties = new Properties();
        try {
            loadProperties(properties, "native-defaults.properties", false);
            loadProperties(properties, "application.properties", true);
            String envPath = System.getenv("VISION_MIND_PATH");
            boolean skipNativeConfig = Boolean.parseBoolean(System.getProperty("vision-mind.skip-opencv", "false"));
            if (envPath == null) {
                if (!skipNativeConfig && !isTestEnvironment()) {
                    log.warn("VISION_MIND_PATH is not defined. Native resources will be resolved relative to the current directory.");
                }
                envPath = "";
            }

            OPENCV_DLL_PATH = envPath + properties.getProperty("opencv.dll.path");
            OPENCV_SO_PATH = envPath + properties.getProperty("opencv.so.path");

            IMG_ONNX = resolvePath(envPath, properties.getProperty("img.onnx"));
            TEXT_ONNX = resolvePath(envPath, properties.getProperty("text.onnx"));
            CLIP_TOKENIZER = resolvePath(envPath, properties.getProperty("clip.tokenizer"));

            VISION_IMAGE_SIZE = Integer.parseInt(getOrDefault(properties, "vision.image.size", "224"));
            VISION_IMAGE_INPUT_NAME = getOrDefault(properties, "vision.image.input", "pixel_values");
            VISION_TEXT_INPUT_IDS_NAME = getOrDefault(properties, "vision.text.input-ids", "input_ids");
            VISION_TEXT_ATTENTION_MASK_NAME = getOrDefault(properties, "vision.text.attention-mask", "attention_mask");
            VISION_IMAGE_MEAN = parseFloatArray(properties, "vision.image.mean",
                    new float[]{0.48145466f, 0.4578275f, 0.40821073f}, 3);
            VISION_IMAGE_STD = parseFloatArray(properties, "vision.image.std",
                    new float[]{0.26862954f, 0.26130258f, 0.27577711f}, 3);

            LUCENE_PATH = resolvePath(envPath, properties.getProperty("lucene.path"));
            VECTOR_STORE_MODE = VectorStoreMode.fromProperty(properties.getProperty("vector.store.mode"));
            ES_URIS = getOrDefault(properties, "es.uris", "http://127.0.0.1:9200");
            ES_USERNAME = trimToNull(properties.getProperty("es.username"));
            ES_PASSWORD = trimToNull(properties.getProperty("es.password"));
            ES_API_KEY = trimToNull(properties.getProperty("es.api-key"));
            ES_TBIR_INDEX = getOrDefault(properties, "es.index.tbir", "vision_mind_tbir");

            OPEN_DETECT = Boolean.valueOf(properties.getProperty("open.detect"));
            DETECT_TYPES = Arrays.stream(properties.getProperty("detect.types", "").split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toSet());

            String[] range = properties.getProperty("filter.box.size", "0,Integer.MAX_VALUE").split(",");
            MIN_SIZE = Integer.parseInt(range[0]);
            MAX_SIZE = Integer.parseInt(range[1]);

            KEY_NUM = Integer.parseInt(properties.getProperty("key.expand.num"));

            AUGMENT_TYPES = Arrays.stream(properties.getProperty("augment.types", "").split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toSet());

        } catch (IOException e) {
            throw new RuntimeException("Failed to read configuration file", e);
        }
    }

    private static void loadProperties(Properties target, String resourceName, boolean required) throws IOException {
        try (InputStream stream = Constant.class.getClassLoader().getResourceAsStream(resourceName)) {
            if (stream == null) {
                if (required) {
                    throw new IOException(resourceName + " not found");
                }
                return;
            }
            target.load(stream);
        }
    }

    private static String getOrDefault(Properties properties, String key, String defaultValue) {
        String value = properties.getProperty(key);
        if (value == null) {
            return defaultValue;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? defaultValue : trimmed;
    }

    private static float[] parseFloatArray(Properties properties, String key, float[] defaultValue, int expectedLength) {
        String value = properties.getProperty(key);
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        String[] parts = value.split(",");
        if (parts.length != expectedLength) {
            throw new IllegalArgumentException(key + " must have " + expectedLength + " comma-separated values");
        }
        float[] result = new float[expectedLength];
        for (int i = 0; i < expectedLength; i++) {
            result[i] = Float.parseFloat(parts[i].trim());
        }
        return result;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String resolvePath(String envPath, String configuredPath) {
        if (configuredPath == null) {
            return envPath;
        }
        String trimmed = configuredPath.trim();
        if (trimmed.isEmpty()) {
            return trimmed;
        }
        if (isAbsolutePath(trimmed)) {
            return trimmed;
        }
        if (envPath == null || envPath.trim().isEmpty()) {
            return Paths.get(trimmed).normalize().toString();
        }
        Path resolved = Paths.get(envPath).resolve(trimmed).normalize();
        return resolved.toString();
    }

    private static boolean isAbsolutePath(String path) {
        if (path.startsWith("/") || path.startsWith("\\")) {
            return true;
        }
        return path.length() >= 3
                && Character.isLetter(path.charAt(0))
                && path.charAt(1) == ':'
                && (path.charAt(2) == '\\' || path.charAt(2) == '/');
    }

    private static boolean isTestEnvironment() {
        try {
            Class.forName("org.junit.jupiter.api.Test");
            return true;
        } catch (ClassNotFoundException ex) {
            return false;
        }
    }
}
