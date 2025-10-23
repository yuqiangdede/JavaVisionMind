package com.yuqiangdede.llm.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yuqiangdede.llm.client.support.HttpJsonClient;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Collections;
import java.util.Objects;
import java.util.Base64;

/**
 * 面向 Ollama 本地服务的对话客户端。
 */
public final class OllamaClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String RESPONSE_FIELD = "response";
    private static final String GENERATE_PATH = "/api/generate";

    private OllamaClient() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 调用 Ollama 生成一段回复文本。
     *
     * @param ollamaBaseUrl Ollama 服务地址，例如 http://127.0.0.1:11434
     * @param ollamaModel   使用的模型名称
     * @param prompt        用户输入的提示词
     */
    public static String chat(String ollamaBaseUrl, String ollamaModel, String prompt) throws IOException {
        return chat(ollamaBaseUrl, ollamaModel, prompt, 0);
    }

    public static String chat(String ollamaBaseUrl, String ollamaModel, String prompt, int timeoutMs) throws IOException {
        Objects.requireNonNull(ollamaBaseUrl, "ollamaBaseUrl");
        Objects.requireNonNull(ollamaModel, "ollamaModel");
        Objects.requireNonNull(prompt, "prompt");

        String responseJson = sendPrompt(ollamaBaseUrl, ollamaModel, prompt, null, timeoutMs);
        JsonNode root = MAPPER.readTree(responseJson);
        JsonNode responseNode = root.get(RESPONSE_FIELD);
        if (responseNode == null || responseNode.isNull()) {
            throw new IOException("Ollama 响应缺少 response 字段");
        }
        return responseNode.asText().trim();
    }

    public static String chatWithImage(String ollamaBaseUrl,
                                       String ollamaModel,
                                       String prompt,
                                       String imageUrl,
                                       int timeoutMs) throws IOException {
        Objects.requireNonNull(ollamaBaseUrl, "ollamaBaseUrl");
        Objects.requireNonNull(ollamaModel, "ollamaModel");
        Objects.requireNonNull(prompt, "prompt");
        Objects.requireNonNull(imageUrl, "imageUrl");

        String encoded = encodeImageToBase64(imageUrl);
        String responseJson = sendPrompt(ollamaBaseUrl, ollamaModel, prompt, encoded, timeoutMs);
        JsonNode root = MAPPER.readTree(responseJson);
        JsonNode responseNode = root.get(RESPONSE_FIELD);
        if (responseNode == null || responseNode.isNull()) {
            throw new IOException("Ollama 响应缺少 response 字段");
        }
        return responseNode.asText().trim();
    }

    private static String sendPrompt(String ollamaBaseUrl,
                                     String ollamaModel,
                                     String prompt,
                                     String imageBase64,
                                     int timeoutMs) throws IOException {
        ObjectNode body = MAPPER.createObjectNode();
        body.put("model", ollamaModel);
        body.put("prompt", prompt);
        body.put("stream", false);
        if (imageBase64 != null) {
            body.putArray("images").add(imageBase64);
        }
        URL url = new URL(normalizeBaseUrl(ollamaBaseUrl) + GENERATE_PATH);
        return HttpJsonClient.post(url, Collections.emptyMap(), body, timeoutMs);
    }

    private static String normalizeBaseUrl(String baseUrl) {
        String trimmed = baseUrl.trim();
        if (trimmed.endsWith("/")) {
            return trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private static String encodeImageToBase64(String imageUrl) throws IOException {
        URL url = new URL(imageUrl);
        try (InputStream inputStream = url.openStream();
             ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
            byte[] chunk = new byte[4096];
            int bytesRead;
            while ((bytesRead = inputStream.read(chunk)) != -1) {
                buffer.write(chunk, 0, bytesRead);
            }
            return Base64.getEncoder().encodeToString(buffer.toByteArray());
        }
    }
}
