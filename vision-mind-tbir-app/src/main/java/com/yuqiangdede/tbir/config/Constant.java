package com.yuqiangdede.tbir.config;


import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
public class Constant {

    // dll so 路径
    public static final String OPENCV_DLL_PATH;
    public static final String OPENCV_SO_PATH;

    public static final String IMG_ONNX;
    public static final String TEXT_ONNX;
    public static final String CLIP_TOKENIZER;

    public static final String LUCENE_PATH;

    public static final Boolean TOKEN_FILTER;
    public static final Boolean OPEN_DETECT;

    public static final int MIN_SIZE;
    public static final int MAX_SIZE;

    public static final int KEY_NUM;

    // 增强类型
    public static final Set<String> AUGMENT_TYPES;

    // 小图向量化的类型
    public static final Set<String> DETECT_TYPES;

    static {
        Properties properties = new Properties();
        InputStream input = null;
        try {
            input = Constant.class.getClassLoader().getResourceAsStream("application.properties");
            properties.load(input);
            String envPath = System.getenv("VISION_MIND_PATH");
            ;

            if (envPath == null) {
                throw new RuntimeException("无法获取环境变量 VISION_MIND_PATH");
            }

            // 从属性文件中获取路径
            OPENCV_DLL_PATH = envPath + properties.getProperty("opencv.dll.path");
            OPENCV_SO_PATH = envPath + properties.getProperty("opencv.so.path");

            IMG_ONNX = envPath + properties.getProperty("img.onnx");
            TEXT_ONNX = envPath + properties.getProperty("text.onnx");
            CLIP_TOKENIZER = envPath + properties.getProperty("clip.tokenizer");

            LUCENE_PATH = envPath + properties.getProperty("lucene.path");

            TOKEN_FILTER = Boolean.valueOf(properties.getProperty("token.filter"));
            OPEN_DETECT = Boolean.valueOf(properties.getProperty("open.detect"));
            DETECT_TYPES = Arrays.stream(properties.getProperty("detect.types", "")
                            .split(","))
                    .map(String::trim)
                    .collect(Collectors.toSet());

            String[] range = properties.getProperty("filter.box.size", "0,Integer.MAX_VALUE").split(",");
            MIN_SIZE = Integer.parseInt(range[0]);
            MAX_SIZE = Integer.parseInt(range[1]);

            KEY_NUM = Integer.parseInt(properties.getProperty("key.expand.num"));

            AUGMENT_TYPES = Arrays.stream(properties.getProperty("augment.types", "")
                            .split(","))
                    .map(String::trim)
                    .collect(Collectors.toSet());

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
