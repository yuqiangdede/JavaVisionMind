package com.yuqiangdede.tts.service;

public class TtsSynthesisResult {

    private final String text;
    private final int voice;
    private final int sampleRate;
    private final float[] samples;

    public TtsSynthesisResult(String text, int voice, int sampleRate, float[] samples) {
        this.text = text;
        this.voice = voice;
        this.sampleRate = sampleRate;
        this.samples = samples;
    }

    public String getText() {
        return text;
    }

    public int getVoice() {
        return voice;
    }

    public int getSampleRate() {
        return sampleRate;
    }

    public float[] getSamples() {
        return samples;
    }
}
