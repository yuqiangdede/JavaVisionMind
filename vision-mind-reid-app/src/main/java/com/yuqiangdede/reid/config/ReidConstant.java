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
            properties.load(input);
            String envPath = System.getenv("VISION_MIND_PATH");

            if (envPath == null) {
                throw new RuntimeException("无法获取环境变量 VISION_MIND_PATH");
            }

            LUCENE_PATH = envPath + properties.getProperty("lucene.path");
            ONNX_PATH = envPath + properties.getProperty("reid.onnx.path");

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
