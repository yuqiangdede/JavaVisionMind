package com.yuqiangdede.tbir.config;

import com.yuqiangdede.tbir.util.ClipEmbedder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import ai.onnxruntime.OrtException;

import static com.yuqiangdede.tbir.config.Constant.*;

@Configuration
public class ClipConfig {

    @Bean
    public ClipEmbedder clipEmbedder() throws OrtException {
        return new ClipEmbedder(
                IMG_ONNX,
                TEXT_ONNX,
                VISION_IMAGE_SIZE,
                VISION_IMAGE_INPUT_NAME,
                VISION_TEXT_INPUT_IDS_NAME,
                VISION_TEXT_ATTENTION_MASK_NAME,
                VISION_IMAGE_MEAN,
                VISION_IMAGE_STD
        );
    }
}
