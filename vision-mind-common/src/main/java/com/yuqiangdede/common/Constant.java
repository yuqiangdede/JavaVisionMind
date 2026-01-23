package com.yuqiangdede.common;


import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

@Slf4j
public class Constant {


    public static final String MATRIX_PATH;


    static {
        Properties properties = new Properties();
        InputStream input = null;
        try {
            input = Constant.class.getClassLoader().getResourceAsStream("application.properties");
            properties.load(input);
            String envPath = System.getenv("VISION_MIND_PATH");

            boolean skipNativeConfig = Boolean.parseBoolean(System.getProperty("vision-mind.skip-opencv", "false"));
            if (envPath == null) {
                if (!skipNativeConfig && !isTestEnvironment()) {
                    log.warn("VISION_MIND_PATH is not defined. Native resources will be resolved relative to the current directory.");
                }
                envPath = "";
            }

            MATRIX_PATH = envPath + properties.getProperty("matrix.path");

        } catch (IOException e) {
            throw new RuntimeException("Failed to read configuration file");
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
