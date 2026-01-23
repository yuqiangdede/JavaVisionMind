package com.yuqiangdede.ffe.config;

import com.yuqiangdede.common.vector.VectorStoreMode;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

@Slf4j
public class Constant {

    // dll so 路径
    public static final String OPENCV_DLL_PATH;
    public static final String OPENCV_SO_PATH;

    public static final String MODEL_SCRFD_PATH;
    public static final String MODEL_COORD_PATH;
    public static final String MODEL_ARC_PATH;
    public static final String MODEL_ARR_PATH;
    public static final String LUCENE_PATH;
    public static final VectorStoreMode VECTOR_STORE_MODE;

    public static final String ES_URIS;
    public static final String ES_USERNAME;
    public static final String ES_PASSWORD;
    public static final String ES_API_KEY;
    public static final String ES_FACE_INDEX;

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

            // 从属性文件中获取路径
            OPENCV_DLL_PATH = envPath + properties.getProperty("opencv.dll.path");
            OPENCV_SO_PATH = envPath + properties.getProperty("opencv.so.path");
            MODEL_SCRFD_PATH = envPath + properties.getProperty("model.scrfd.path");
            MODEL_COORD_PATH = envPath + properties.getProperty("model.coord.path");
            MODEL_ARC_PATH = envPath + properties.getProperty("model.arc.path");
            MODEL_ARR_PATH = envPath + properties.getProperty("model.arr.path");
            LUCENE_PATH = envPath + properties.getProperty("lucene.path");

            VECTOR_STORE_MODE = VectorStoreMode.fromProperty(properties.getProperty("vector.store.mode"));
            ES_URIS = getOrDefault(properties, "es.uris", "http://127.0.0.1:9200");
            ES_USERNAME = trimToNull(properties.getProperty("es.username"));
            ES_PASSWORD = trimToNull(properties.getProperty("es.password"));
            ES_API_KEY = trimToNull(properties.getProperty("es.api-key"));
            ES_FACE_INDEX = getOrDefault(properties, "es.index.face", "vision_mind_face");
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

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
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
