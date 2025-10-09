package com.yuqiangdede.reid.config;

import com.yuqiangdede.common.vector.VectorStoreMode;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

@Slf4j
public class ReidConstant {

    public static final String LUCENE_PATH;
    public static final String ONNX_PATH;
    public static final VectorStoreMode VECTOR_STORE_MODE;

    public static final String ES_URIS;
    public static final String ES_USERNAME;
    public static final String ES_PASSWORD;
    public static final String ES_API_KEY;
    public static final String ES_REID_INDEX;

    static {
        Properties properties = new Properties();
        InputStream input = null;
        try {
            input = ReidConstant.class.getClassLoader().getResourceAsStream("application.properties");
            if (input == null) {
                throw new RuntimeException("application.properties not found");
            }
            properties.load(input);
            String envPath = System.getenv("VISION_MIND_PATH");
            boolean skipNativeConfig = Boolean.parseBoolean(System.getProperty("vision-mind.skip-opencv", "false"));
            if (envPath == null) {
                if (!skipNativeConfig && !isTestEnvironment()) {
                    log.warn("VISION_MIND_PATH is not defined. Native resources will be resolved relative to the current directory.");
                }
                envPath = "";
            }

            LUCENE_PATH = envPath + properties.getProperty("lucene.path");
            ONNX_PATH = envPath + properties.getProperty("reid.onnx.path");

            VECTOR_STORE_MODE = VectorStoreMode.fromProperty(properties.getProperty("vector.store.mode"));
            ES_URIS = getOrDefault(properties, "es.uris", "http://127.0.0.1:9200");
            ES_USERNAME = trimToNull(properties.getProperty("es.username"));
            ES_PASSWORD = trimToNull(properties.getProperty("es.password"));
            ES_API_KEY = trimToNull(properties.getProperty("es.api-key"));
            ES_REID_INDEX = getOrDefault(properties, "es.index.reid", "vision_mind_reid");

        } catch (IOException e) {
            throw new RuntimeException("Failed to read configuration file", e);
        } finally {
            if (input != null) {
                try {
                    input.close();
                } catch (IOException e) {
                    log.error("read application.properties error", e);
                }
            }
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
