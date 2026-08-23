package com.yuqiangdede.common;

import com.yuqiangdede.common.config.YamlConfig;

public class Constant {

    public static final String MATRIX_PATH;

    static {
        YamlConfig config = YamlConfig.load(Constant.class);
        String envPath = System.getenv("VISION_MIND_PATH");
        if (envPath == null) {
            envPath = "";
        }
        MATRIX_PATH = envPath + config.get("vision-mind.common.matrix-path", "/reid/projectionMatrix.bin");
    }

}
