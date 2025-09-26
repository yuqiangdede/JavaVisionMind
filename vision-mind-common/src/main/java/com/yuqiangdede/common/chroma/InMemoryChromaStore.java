package com.yuqiangdede.common.chroma;

import com.yuqiangdede.common.util.VectorUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Thread-safe in-memory implementation that mimics chroma-java's collection behaviour.
 */
public class InMemoryChromaStore implements ChromaStore {

    private final Map<String, EmbeddingRecord> records = new ConcurrentHashMap<>();

    @Override
    public void upsert(EmbeddingRecord record) {
        Objects.requireNonNull(record, "record");
        records.put(record.getId(), record);
    }

    @Override
    public void delete(String id) {
        if (id != null) {
            records.remove(id);
        }
    }

    @Override
    public void clear() {
        records.clear();
    }

    @Override
    public List<SearchResult> similaritySearch(float[] queryVector,
                                               int topK,
                                               Map<String, String> filter,
                                               double minScore) {
        if (records.isEmpty()) {
            return Collections.emptyList();
        }
        float[] normalized = VectorUtil.normalizeVector(queryVector);
        Map<String, String> effectiveFilter = filter == null ? Collections.emptyMap() : filter;

        List<SearchResult> candidates = new ArrayList<>();
        for (EmbeddingRecord record : records.values()) {
            if (!matchesFilter(record, effectiveFilter)) {
                continue;
            }
            double similarity = VectorUtil.calculateCosineSimilarity(normalized, record.getEmbedding());
            if (similarity >= minScore) {
                candidates.add(new SearchResult(record, similarity));
            }
        }

        return candidates.stream()
                .sorted(Comparator.naturalOrder())
                .limit(Math.max(topK, 0))
                .collect(Collectors.toList());
    }

    private boolean matchesFilter(EmbeddingRecord record, Map<String, String> filter) {
        if (filter.isEmpty()) {
            return true;
        }
        Map<String, String> metadata = record.getMetadata();
        for (Map.Entry<String, String> entry : filter.entrySet()) {
            if (!Objects.equals(metadata.get(entry.getKey()), entry.getValue())) {
                return false;
            }
        }
        return true;
    }

    @Override
    public List<EmbeddingRecord> find(Map<String, String> filter) {
        if (records.isEmpty()) {
            return Collections.emptyList();
        }
        Map<String, String> effectiveFilter = filter == null ? Collections.emptyMap() : filter;
        List<EmbeddingRecord> matches = new ArrayList<>();
        for (EmbeddingRecord record : records.values()) {
            if (matchesFilter(record, effectiveFilter)) {
                matches.add(record);
            }
        }
        return matches;
    }
}
