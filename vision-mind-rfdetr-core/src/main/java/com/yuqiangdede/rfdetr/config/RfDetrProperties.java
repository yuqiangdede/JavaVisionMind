package com.yuqiangdede.rfdetr.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "vision-mind.rfdetr")
public class RfDetrProperties {

    private String modelPath = "rfdetr/model/rfdetr-small.onnx";
    private String metadataPath = "rfdetr/model/rfdetr-small.metadata.json";
    private float threshold = 0.3f;
    private String defaultTypes = "1,2,3,4,5,6,7,8,9";
    private double detectionRatio = 0.5d;
    private double blockingRatio = 0.5d;
    private int frameInterval = 5;
    private int threads;

    public String getModelPath() {
        return modelPath;
    }

    public void setModelPath(String modelPath) {
        this.modelPath = modelPath;
    }

    public String getMetadataPath() {
        return metadataPath;
    }

    public void setMetadataPath(String metadataPath) {
        this.metadataPath = metadataPath;
    }

    public float getThreshold() {
        return threshold;
    }

    public void setThreshold(float threshold) {
        this.threshold = threshold;
    }

    public String getDefaultTypes() {
        return defaultTypes;
    }

    public void setDefaultTypes(String defaultTypes) {
        this.defaultTypes = defaultTypes;
    }

    public double getDetectionRatio() {
        return detectionRatio;
    }

    public void setDetectionRatio(double detectionRatio) {
        this.detectionRatio = detectionRatio;
    }

    public double getBlockingRatio() {
        return blockingRatio;
    }

    public void setBlockingRatio(double blockingRatio) {
        this.blockingRatio = blockingRatio;
    }

    public int getFrameInterval() {
        return frameInterval;
    }

    public void setFrameInterval(int frameInterval) {
        this.frameInterval = frameInterval;
    }

    public int getThreads() {
        return threads;
    }

    public void setThreads(int threads) {
        this.threads = threads;
    }
}
