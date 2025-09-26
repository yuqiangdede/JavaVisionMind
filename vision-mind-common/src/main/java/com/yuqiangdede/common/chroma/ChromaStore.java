package com.yuqiangdede.common.chroma;

import java.util.List;
import java.util.Map;

/**
 * Minimal in-memory vector store inspired by chroma-java. The implementation
 * intentionally mirrors the semantics of the original project so that modules
 * depending on chroma-java can be embedded without an external dependency.
 */
public interface ChromaStore {

    /**
     * Insert or replace the supplied record.
     *
     * @param record embedding payload to persist
     */
    void upsert(EmbeddingRecord record);

    /**
     * Remove a record by its identifier.
     *
     * @param id logical identifier
     */
    void delete(String id);

    /**
     * Remove all records from the collection.
     */
    void clear();

    /**
     * Execute a similarity search.
     *
     * @param queryVector normalized query vector
     * @param topK maximum number of hits to return
     * @param filter optional equality filter applied against record metadata
     * @param minScore minimum cosine similarity required for the result to be returned
     * @return ranked search result list ordered by similarity
     */
    List<SearchResult> similaritySearch(float[] queryVector, int topK, Map<String, String> filter, double minScore);

    /**
     * Return records matching the provided metadata filter.
     *
     * @param filter equality filter
     * @return matching records
     */
    List<EmbeddingRecord> find(Map<String, String> filter);
}
