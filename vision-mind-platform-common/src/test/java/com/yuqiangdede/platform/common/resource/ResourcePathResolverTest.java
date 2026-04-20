package com.yuqiangdede.platform.common.resource;

import com.yuqiangdede.platform.common.config.VisionMindProperties;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourcePathResolverTest {

    @Test
    void resolveResourcePathPrefersLocalRoot() {
        VisionMindProperties properties = new VisionMindProperties();
        properties.getResource().setRoot(resourceRoot().toString());
        ResourcePathResolver resolver = new ResourcePathResolver(properties);
        Path resolved = resolver.resolve("resource/yolo/model/yolo26s.onnx");
        assertTrue(resolved.toString().replace("\\", "/").contains("/resource/yolo/model/yolo26s.onnx"));
    }

    private Path resourceRoot() {
        Path local = Paths.get("resource");
        if (Files.exists(local)) {
            return local;
        }
        return Paths.get("..", "resource");
    }
}
