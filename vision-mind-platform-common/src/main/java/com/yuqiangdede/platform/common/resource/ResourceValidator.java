package com.yuqiangdede.platform.common.resource;

import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class ResourceValidator {
    private final ResourcePathResolver pathResolver;
    private final ResourceManifestLoader manifestLoader;

    public ResourceValidator(ResourcePathResolver pathResolver, ResourceManifestLoader manifestLoader) {
        this.pathResolver = pathResolver;
        this.manifestLoader = manifestLoader;
    }

    public List<Path> validateRequired(String module) throws IOException {
        List<Path> missing = new ArrayList<>();
        for (JsonNode item : manifestLoader.required(module)) {
            Path path = pathResolver.resolve(item.path("path").asText());
            if (!java.nio.file.Files.exists(path)) {
                missing.add(path);
            }
        }
        return missing;
    }

    public void validateOrThrow(String module) {
        try {
            List<Path> missing = validateRequired(module);
            if (!missing.isEmpty()) {
                throw new IllegalStateException("RESOURCE_MISSING: " + missing);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Unable to read resource manifest", e);
        }
    }
}
