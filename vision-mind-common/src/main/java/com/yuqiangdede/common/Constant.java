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

            if (envPath == null) {
                throw new RuntimeException("无法获取环境变量 VISION_MIND_PATH");
            }

            MATRIX_PATH = envPath + properties.getProperty("matrix.path");

        } catch (IOException e) {
            throw new RuntimeException("读取配置文件失败");
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
}
