package com.yuqiangdede.common.chroma;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * Lightweight DTO describing an embedding alongside its metadata.
 */
public final class EmbeddingRecord {

    private final String id;
    private final float[] embedding;
    private final Map<String, String> metadata;
    private final Map<String, Object> payload;
    private final long timestamp;

    public EmbeddingRecord(String id,
                           float[] embedding,
                           Map<String, String> metadata,
                           Map<String, Object> payload,
                           long timestamp) {
        this.id = Objects.requireNonNull(id, "id");
        this.embedding = Objects.requireNonNull(embedding, "embedding");
        this.metadata = metadata == null ? Collections.emptyMap() : Collections.unmodifiableMap(metadata);
        this.payload = payload == null ? Collections.emptyMap() : Collections.unmodifiableMap(payload);
        this.timestamp = timestamp;
    }

    public String getId() {
        return id;
    }

    public float[] getEmbedding() {
        return embedding;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }

    public Map<String, Object> getPayload() {
        return payload;
    }

    public long getTimestamp() {
        return timestamp;
    }
}
