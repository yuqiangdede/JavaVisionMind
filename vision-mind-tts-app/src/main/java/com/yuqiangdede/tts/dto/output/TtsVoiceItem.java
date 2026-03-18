package com.yuqiangdede.tts.dto.output;

public class TtsVoiceItem {

    private int id;
    private String name;
    private boolean isDefault;

    public TtsVoiceItem() {
    }

    public TtsVoiceItem(int id, String name, boolean isDefault) {
        this.id = id;
        this.name = name;
        this.isDefault = isDefault;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isDefault() {
        return isDefault;
    }

    public void setDefault(boolean aDefault) {
        isDefault = aDefault;
    }
}
