package com.yuqiangdede.platform.common.resource;

import com.yuqiangdede.platform.common.config.VisionMindProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourcePathResolverTest {

    @Test
    void resolveResourcePathPrefersLocalRoot() {
        VisionMindProperties properties = new VisionMindProperties();
        properties.getResource().setRoot(resourceRoot().toString());
        ResourcePathResolver resolver = new ResourcePathResolver(properties);
        Path resolved = resolver.resolve("resource/yolo/model/yolo26s.onnx");
        assertTrue(Files.isRegularFile(resolved), () -> "resolved path does not exist: " + resolved);
    }

    @Test
    void resourceRootFallsBackToParentProjectResourceWhenConfiguredRootIsMissing(@TempDir Path checkout) throws IOException {
        Path projectRoot = checkout.resolve("project");
        Path moduleRoot = projectRoot.resolve("vision-mind-yolo-app");
        Path projectResource = projectRoot.resolve("resource");
        Files.createDirectories(moduleRoot);
        Files.createDirectories(projectResource);

        VisionMindProperties properties = new VisionMindProperties();
        properties.getResource().setRoot(moduleRoot.resolve("resource").toString());
        properties.getResource().setFallbackEnv("VISION_MIND_TEST_RESOURCE_ENV_NOT_SET");

        Path resolved = new ResourcePathResolver(properties).resourceRoot();

        assertEquals(projectResource.toAbsolutePath().normalize(), resolved);
    }

    private Path resourceRoot() {
        Path local = Paths.get("resource");
        if (Files.exists(local)) {
            return local;
        }
        return Paths.get("..", "resource");
    }
}
