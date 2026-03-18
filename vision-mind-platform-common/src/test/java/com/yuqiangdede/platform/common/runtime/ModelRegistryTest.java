package com.yuqiangdede.platform.common.runtime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ModelRegistryTest {

    @Test
    void registerAndLookupWorks() {
        ModelRegistry registry = new ModelRegistry();
        registry.register(new ModelDescriptor("demo", "resource/yolo/model/yolo26s.onnx", true));
        assertEquals("demo", registry.get("demo").getName());
    }

    @Test
    void requiredMissingModelShouldThrow() {
        ModelRegistry registry = new ModelRegistry();
        registry.register(new ModelDescriptor("missing", "resource/not-exist/model.onnx", true));
        assertThrows(IllegalStateException.class, registry::validateRequiredModels);
    }
}
