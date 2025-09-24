package com.yuqiangdede.ffe.config;


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

    public static final Boolean TOKEN_FILTER;

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

            // 从属性文件中获取路径
            OPENCV_DLL_PATH = envPath + properties.getProperty("opencv.dll.path");
            OPENCV_SO_PATH = envPath + properties.getProperty("opencv.so.path");
            MODEL_SCRFD_PATH = envPath + properties.getProperty("model.scrfd.path");
            MODEL_COORD_PATH = envPath + properties.getProperty("model.coord.path");
            MODEL_ARC_PATH = envPath + properties.getProperty("model.arc.path");
            MODEL_ARR_PATH = envPath + properties.getProperty("model.arr.path");
            LUCENE_PATH = envPath + properties.getProperty("lucene.path");

            TOKEN_FILTER = Boolean.valueOf(properties.getProperty("token.filter"));
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
