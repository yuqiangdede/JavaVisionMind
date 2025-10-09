package com.yuqiangdede.common.vector;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;

import org.apache.http.Header;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.message.BasicHeader;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.elasticsearch.client.RestHighLevelClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Factory for creating Elasticsearch {@link RestHighLevelClient} instances based on configuration.
 */
public final class ElasticsearchClientFactory {
    private static final Logger LOGGER = LoggerFactory.getLogger(ElasticsearchClientFactory.class);

    private ElasticsearchClientFactory() {
    }

    /**
     * Create a {@link RestHighLevelClient} using the provided URIs and optional authentication.
     *
     * @param uris comma separated URIs (e.g. {@code http://127.0.0.1:9200})
     * @param username username for basic auth (optional)
     * @param password password for basic auth (optional)
     * @param apiKey encoded API key value (optional, plain value that will be base64-encoded)
     * @return configured client
     */
    public static RestHighLevelClient createClient(
            String uris,
            String username,
            String password,
            String apiKey) {
        List<HttpHost> hosts = parseHosts(uris);
        RestClientBuilder builder = RestClient.builder(hosts.toArray(new HttpHost[0]));

        CredentialsProvider credentialsProvider = null;
        if (username != null && !username.isBlank() && password != null) {
            credentialsProvider = new BasicCredentialsProvider();
            credentialsProvider.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(username, password));
            CredentialsProvider finalProvider = credentialsProvider;
            builder.setHttpClientConfigCallback(httpClientBuilder ->
                    httpClientBuilder.setDefaultCredentialsProvider(finalProvider));
        }

        if (apiKey != null && !apiKey.isBlank()) {
            String encoded = Base64.getEncoder().encodeToString(apiKey.getBytes(StandardCharsets.UTF_8));
            Header apiKeyHeader = new BasicHeader("Authorization", "ApiKey " + encoded);
            builder.setDefaultHeaders(new Header[]{apiKeyHeader});
        }

        builder.setRequestConfigCallback(requestConfigBuilder ->
                requestConfigBuilder
                        .setConnectTimeout(10_000)
                        .setSocketTimeout(60_000));

        return new RestHighLevelClient(builder);
    }

    private static List<HttpHost> parseHosts(String uris) {
        if (uris == null || uris.isBlank()) {
            return Collections.singletonList(HttpHost.create("http://127.0.0.1:9200"));
        }
        String[] parts = uris.split(",");
        List<HttpHost> hosts = new ArrayList<>(parts.length);
        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                hosts.add(HttpHost.create(trimmed));
            }
        }
        if (hosts.isEmpty()) {
            hosts.add(HttpHost.create("http://127.0.0.1:9200"));
        }
        return hosts;
    }
}
