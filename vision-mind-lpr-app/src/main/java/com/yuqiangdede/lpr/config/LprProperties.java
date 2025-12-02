package com.yuqiangdede.lpr.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
@Data
@ConfigurationProperties(prefix = "lpr")
public class LprProperties {

    /**
     * Relative or absolute model path. When relative it is resolved against VISION_MIND_PATH.
     */
    private String modelPath = "/lpr/model/lprnet.onnx";

    private int modelInputWidth = 94;
    private int modelInputHeight = 24;
}
