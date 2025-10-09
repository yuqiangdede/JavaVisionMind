package com.yuqiangdede.common.vector;

import java.util.Objects;

/**
 * Minimal configuration holder for Elasticsearch connectivity and target index.
 */
public final class ElasticsearchConfig {
    private final String uris;
    private final String username;
    private final String password;
    private final String apiKey;
    private final String index;

    public ElasticsearchConfig(String uris, String username, String password, String apiKey, String index) {
        this.uris = uris;
        this.username = username;
        this.password = password;
        this.apiKey = apiKey;
        this.index = index;
    }

    public String getUris() {
        return uris;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public String getApiKey() {
        return apiKey;
    }

    public String getIndex() {
        return index;
    }

    public boolean hasValidIndex() {
        return index != null && !index.isBlank();
    }
}
