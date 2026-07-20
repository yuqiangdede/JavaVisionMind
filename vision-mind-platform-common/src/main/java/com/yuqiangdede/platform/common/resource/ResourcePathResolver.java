package com.yuqiangdede.platform.common.resource;

import com.yuqiangdede.platform.common.config.VisionMindProperties;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ResourcePathResolver {
    private final VisionMindProperties properties;

    public ResourcePathResolver(VisionMindProperties properties) {
        this.properties = properties;
    }

    public Path resourceRoot() {
        Path configured = Paths.get(properties.getResource().getRoot()).toAbsolutePath().normalize();
        if (Files.exists(configured)) {
            return configured;
        }
        String fallback = System.getenv(properties.getResource().getFallbackEnv());
        if (fallback != null && !fallback.isBlank()) {
            return Paths.get(fallback).toAbsolutePath().normalize();
        }
        return configured;
    }

    public Path resolve(String path) {
        Path candidate = Paths.get(path);
        if (candidate.isAbsolute()) {
            return candidate.normalize();
        }
        String normalized = path.replace('\\', '/');
        if (normalized.equals("resource") || normalized.startsWith("resource/")) {
            normalized = normalized.substring("resource".length());
            while (normalized.startsWith("/")) {
                normalized = normalized.substring(1);
            }
        }
        return resourceRoot().resolve(normalized).normalize();
    }
}
