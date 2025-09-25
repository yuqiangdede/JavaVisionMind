package com.yuqiangdede.tbir.config;

import com.yuqiangdede.tbir.util.ClipEmbedder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import ai.onnxruntime.OrtException;

import static com.yuqiangdede.tbir.config.Constant.IMG_ONNX;
import static com.yuqiangdede.tbir.config.Constant.TEXT_ONNX;

@Configuration
public class ClipConfig {

    @Bean
    public ClipEmbedder clipEmbedder() throws OrtException {
        return new ClipEmbedder(IMG_ONNX, TEXT_ONNX);
    }
}
