package com.yuqiangdede.tts.dto.output;

import java.util.List;

public class TtsVoicesResponse {

    private String model;
    private int sampleRate;
    private int defaultVoice;
    private List<TtsVoiceItem> voices;

    public TtsVoicesResponse() {
    }

    public TtsVoicesResponse(String model, int sampleRate, int defaultVoice, List<TtsVoiceItem> voices) {
        this.model = model;
        this.sampleRate = sampleRate;
        this.defaultVoice = defaultVoice;
        this.voices = voices;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
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

    public List<TtsVoiceItem> getVoices() {
        return voices;
    }

    public void setVoices(List<TtsVoiceItem> voices) {
        this.voices = voices;
    }
}
