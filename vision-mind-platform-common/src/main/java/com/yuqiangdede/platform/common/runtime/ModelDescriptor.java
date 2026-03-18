package com.yuqiangdede.platform.common.runtime;

public class ModelDescriptor {

    private final String name;
    private final String path;
    private final boolean required;

    public ModelDescriptor(String name, String path, boolean required) {
        this.name = name;
        this.path = path;
        this.required = required;
    }

    public String getName() {
        return name;
    }

    public String getPath() {
        return path;
    }

    public boolean isRequired() {
        return required;
    }
}
