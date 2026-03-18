package com.yuqiangdede.platform.common.runtime;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public class ModelRegistry {

    private final Map<String, ModelDescriptor> modelMap = new LinkedHashMap<>();

    public void register(ModelDescriptor descriptor) {
        modelMap.put(descriptor.getName(), descriptor);
    }

    public ModelDescriptor get(String name) {
        return modelMap.get(name);
    }

    public Collection<ModelDescriptor> list() {
        return modelMap.values();
    }

    public void validateRequiredModels() {
        for (ModelDescriptor descriptor : modelMap.values()) {
            if (!descriptor.isRequired()) {
                continue;
            }
            if (!Files.isRegularFile(Path.of(descriptor.getPath()))) {
                throw new IllegalStateException("missing required model: " + descriptor.getName() + " -> " + descriptor.getPath());
            }
        }
    }
}
