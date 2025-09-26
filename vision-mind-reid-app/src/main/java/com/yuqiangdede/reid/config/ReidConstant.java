package com.yuqiangdede.reid.config;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

@Slf4j
public class ReidConstant {

    public static final String LUCENE_PATH;
    public static final String ONNX_PATH;

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

    private static boolean isTestEnvironment() {
        try {
            Class.forName("org.junit.jupiter.api.Test");
            return true;
        } catch (ClassNotFoundException ex) {
            return false;
        }
    }
}
