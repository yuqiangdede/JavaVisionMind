package com.yuqiangdede.tts.dto.output;

public class TtsHealthResponse {

    private boolean ready;
    private String message;
    private String model;
    private String modelDir;
    private String runtimeJavaJar;
    private String runtimeNativeJar;
    private int numSpeakers;
    private int sampleRate;
    private int defaultVoice;

    public TtsHealthResponse() {
    }

    public TtsHealthResponse(boolean ready, String message, String model, String modelDir,
                             String runtimeJavaJar, String runtimeNativeJar, int numSpeakers, int sampleRate,
                             int defaultVoice) {
        this.ready = ready;
        this.message = message;
        this.model = model;
        this.modelDir = modelDir;
        this.runtimeJavaJar = runtimeJavaJar;
        this.runtimeNativeJar = runtimeNativeJar;
        this.numSpeakers = numSpeakers;
        this.sampleRate = sampleRate;
        this.defaultVoice = defaultVoice;
    }

    public boolean isReady() {
        return ready;
    }

    public void setReady(boolean ready) {
        this.ready = ready;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getModelDir() {
        return modelDir;
    }

    public void setModelDir(String modelDir) {
        this.modelDir = modelDir;
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

    public int getNumSpeakers() {
        return numSpeakers;
    }

    public void setNumSpeakers(int numSpeakers) {
        this.numSpeakers = numSpeakers;
    }

    public int getSampleRate() {
        return sampleRate;
    }

    public void setSampleRate(int sampleRate) {
        this.sampleRate = sampleRate;
    }

    public int getDefaultVoice() {
        return defaultVoice;
    }

    public void setDefaultVoice(int defaultVoice) {
        this.defaultVoice = defaultVoice;
    }
}
