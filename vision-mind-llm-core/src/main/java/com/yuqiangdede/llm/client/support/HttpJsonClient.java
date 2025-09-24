package com.yuqiangdede.llm.client.support;

import com.fasterxml.jackson.databind.JsonNode;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;

/**
 * 面向大模型集成的轻量级 JSON HTTP 工具类。
 */
public final class HttpJsonClient {

    private static final int DEFAULT_TIMEOUT_MS = 30_000;
    private static final String METHOD_POST = "POST";
    private static final String HEADER_CONTENT_TYPE = "Content-Type";
    private static final String CONTENT_TYPE_JSON = "application/json";

    private HttpJsonClient() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static String post(URL url, Map<String, String> headers, JsonNode body) throws IOException {
        Objects.requireNonNull(url, "url");
        Objects.requireNonNull(body, "body");

        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod(METHOD_POST);
        connection.setDoOutput(true);
        connection.setConnectTimeout(DEFAULT_TIMEOUT_MS);
        connection.setReadTimeout(DEFAULT_TIMEOUT_MS);
        connection.setRequestProperty(HEADER_CONTENT_TYPE, CONTENT_TYPE_JSON);

        if (headers != null) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    connection.setRequestProperty(entry.getKey(), entry.getValue());
                }
            }
        }

        byte[] payload = body.toString().getBytes(StandardCharsets.UTF_8);
        try (OutputStream outputStream = connection.getOutputStream()) {
            outputStream.write(payload);
        }

        int status = connection.getResponseCode();
        InputStream responseStream = status >= HttpURLConnection.HTTP_OK && status < HttpURLConnection.HTTP_MULT_CHOICE
                ? connection.getInputStream()
                : connection.getErrorStream();

        if (responseStream == null) {
            connection.disconnect();
            throw new IOException("未能从 " + url + " 读取到响应内容");
        }

        String responseBody;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(responseStream, StandardCharsets.UTF_8))) {
            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }
            responseBody = builder.toString();
        } finally {
            connection.disconnect();
        }

        if (status < HttpURLConnection.HTTP_OK || status >= HttpURLConnection.HTTP_MULT_CHOICE) {
            throw new IOException(String.format("调用 %s 失败，HTTP 状态码：%d，响应：%s", url, status, responseBody));
        }

        return responseBody;
    }
}
