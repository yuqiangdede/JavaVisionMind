package com.yuqiangdede.platform.common.image;

public class ImageSource {

    public enum Type {
        URL,
        BASE64,
        UPLOAD
    }

    private Type type = Type.URL;
    private String url;
    private String base64;

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getBase64() {
        return base64;
    }

    public void setBase64(String base64) {
        this.base64 = base64;
    }
}
