package com.yuqiangdede.platform.common.resource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuqiangdede.platform.common.config.VisionMindProperties;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.stream.StreamSupport;

public class ResourceManifestLoader {
    private final ObjectMapper objectMapper;
    private final VisionMindProperties properties;
    private final ResourcePathResolver pathResolver;

    public ResourceManifestLoader(ObjectMapper objectMapper, VisionMindProperties properties,
                                  ResourcePathResolver pathResolver) {
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.pathResolver = pathResolver;
    }

    public List<JsonNode> required(String module) throws IOException {
        Path manifest = pathResolver.resolve(properties.getResource().getManifest());
        if (!Files.exists(manifest)) {
            return Collections.emptyList();
        }
        JsonNode modules = objectMapper.readTree(manifest.toFile()).path("modules");
        for (JsonNode item : modules) {
            if (module.equals(item.path("name").asText())) {
                return StreamSupport.stream(item.path("required").spliterator(), false).toList();
            }
        }
        return Collections.emptyList();
    }
}
