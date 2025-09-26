package com.yuqiangdede.common.chroma;

/**
 * SearchResult pairs an {@link EmbeddingRecord} with its cosine similarity score.
 */
public final class SearchResult implements Comparable<SearchResult> {

    private final EmbeddingRecord record;
    private final double score;

    public SearchResult(EmbeddingRecord record, double score) {
        this.record = record;
        this.score = score;
    }

    public EmbeddingRecord getRecord() {
        return record;
    }

    public double getScore() {
        return score;
    }

    @Override
    public int compareTo(SearchResult other) {
        return Double.compare(other.score, this.score);
    }
}
