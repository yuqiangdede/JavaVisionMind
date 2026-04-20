package com.yuqiangdede.platform.common.resource;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuqiangdede.platform.common.config.VisionMindProperties;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ResourceValidatorTest {

    @Test
    void validateRequiredReturnsMissingItems() throws Exception {
        Path tempManifest = Files.createTempFile("manifest", ".json");
        String json = """
                {
                  "modules":[
                    {
                      "name":"demo",
                      "required":[{"path":"__not_exists__/a.onnx","desc":"missing"}]
                    }
                  ]
                }
                """;
        Files.writeString(tempManifest, json);

        VisionMindProperties properties = new VisionMindProperties();
        properties.getResource().setRoot(resourceRoot().toString());
        properties.getResource().setManifest(tempManifest.toString());
        ResourcePathResolver resolver = new ResourcePathResolver(properties);
        ResourceManifestLoader loader = new ResourceManifestLoader(new ObjectMapper(), properties, resolver);
        ResourceValidator validator = new ResourceValidator(resolver, loader);

        List<Path> missing = validator.validateRequired("demo");
        assertEquals(1, missing.size());
    }

    private Path resourceRoot() {
        Path local = Paths.get("resource");
        if (Files.exists(local)) {
            return local;
        }
        return Paths.get("..", "resource");
    }
}
