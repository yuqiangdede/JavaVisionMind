package com.yuqiangdede.asr.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "vision-mind.asr")
public class AsrRuntimeProperties {

    private String modelDir = "resource/asr/model/sherpa-onnx-streaming-zipformer-zh-fp16-2025-06-30";
    private String punctuationModelDir = "resource/asr/model/sherpa-onnx-punct-ct-transformer-zh-en-vocab272727-2024-04-12-int8";
    private String configDir = "resource/asr/config";
    private String uploadDir = "resource/asr/uploads";
    private String hotwordsDir = "resource/asr/model/sherpa-onnx-streaming-zipformer-zh-fp16-2025-06-30/.hotwords";
    private String runtimeJavaJar = "auto";
    private String runtimeNativeJar = "auto";
    private String runtimeProvider = "cpu";
    private int numThreads = 2;
    private float hotwordsScore = 1.5f;
    private String decodingMethod = "modified_beam_search";

    public String getModelDir() {
        return modelDir;
    }

    public void setModelDir(String modelDir) {
        this.modelDir = modelDir;
    }

    public String getPunctuationModelDir() {
        return punctuationModelDir;
    }

    public void setPunctuationModelDir(String punctuationModelDir) {
        this.punctuationModelDir = punctuationModelDir;
    }

    public String getConfigDir() {
        return configDir;
    }

    public void setConfigDir(String configDir) {
        this.configDir = configDir;
    }

    public String getUploadDir() {
        return uploadDir;
    }

    public void setUploadDir(String uploadDir) {
        this.uploadDir = uploadDir;
    }

    public String getHotwordsDir() {
        return hotwordsDir;
    }

    public void setHotwordsDir(String hotwordsDir) {
        this.hotwordsDir = hotwordsDir;
    }

    public String getRuntimeJavaJar() {
        return runtimeJavaJar;
    }

    public void setRuntimeJavaJar(String runtimeJavaJar) {
        this.runtimeJavaJar = runtimeJavaJar;
    }

    public String getRuntimeNativeJar() {
        return runtimeNativeJar;
    }

    public void setRuntimeNativeJar(String runtimeNativeJar) {
        this.runtimeNativeJar = runtimeNativeJar;
    }

    public String getRuntimeProvider() {
        return runtimeProvider;
    }

    public void setRuntimeProvider(String runtimeProvider) {
        this.runtimeProvider = runtimeProvider;
    }

    public int getNumThreads() {
        return numThreads;
    }

    public void setNumThreads(int numThreads) {
        this.numThreads = numThreads;
    }

    public float getHotwordsScore() {
        return hotwordsScore;
    }

    public void setHotwordsScore(float hotwordsScore) {
        this.hotwordsScore = hotwordsScore;
    }

    public String getDecodingMethod() {
        return decodingMethod;
    }

    public void setDecodingMethod(String decodingMethod) {
        this.decodingMethod = decodingMethod;
    }
}
