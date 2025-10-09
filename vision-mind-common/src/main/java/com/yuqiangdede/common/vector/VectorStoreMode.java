package com.yuqiangdede.common.vector;

import java.util.Locale;

/**
 * Supported backing stores for vector persistence and retrieval.
 */
public enum VectorStoreMode {
    LUCENE,
    MEMORY,
    ELASTICSEARCH;

    /**
     * Parse a configuration value into a {@link VectorStoreMode}. Defaults to {@code LUCENE}
     * when the input is {@code null}, blank, or unrecognised.
     *
     * @param raw configuration value
     * @return resolved mode
     */
    public static VectorStoreMode fromProperty(String raw) {
        if (raw == null) {
            return LUCENE;
        }
        String normalized = raw.trim();
        if (normalized.isEmpty()) {
            return LUCENE;
        }
        normalized = normalized.replace('-', '_').toUpperCase(Locale.ROOT);
        for (VectorStoreMode mode : values()) {
            if (mode.name().equals(normalized)) {
                return mode;
            }
        }
        switch (normalized) {
            case "ES" -> {
                return ELASTICSEARCH;
            }
            case "IN_MEMORY", "INMEMORY" -> {
                return MEMORY;
            }
            default -> {
                return LUCENE;
            }
        }
    }
}
