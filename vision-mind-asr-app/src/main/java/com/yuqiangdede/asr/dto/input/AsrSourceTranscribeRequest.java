package com.yuqiangdede.asr.dto.input;

public class AsrSourceTranscribeRequest {

    private String audioUrl;
    private String audioBase64;
    private String fileName = "audio.wav";
    private Boolean enablePunctuation = false;

    public String getAudioUrl() {
        return audioUrl;
    }

    public void setAudioUrl(String audioUrl) {
        this.audioUrl = audioUrl;
    }

    public String getAudioBase64() {
        return audioBase64;
    }

    public void setAudioBase64(String audioBase64) {
        this.audioBase64 = audioBase64;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public Boolean getEnablePunctuation() {
        return enablePunctuation;
    }

    public void setEnablePunctuation(Boolean enablePunctuation) {
        this.enablePunctuation = enablePunctuation;
    }
}
