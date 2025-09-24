package com.yuqiangdede.llm.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yuqiangdede.llm.client.support.HttpJsonClient;

import java.io.IOException;
import java.net.URL;
import java.util.Collections;
import java.util.Objects;

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
     * 通过 Ollama 本地服务进行对话推理。
     *
     * @param ollamaBaseUrl Ollama 服务地址，例如 http://127.0.0.1:11434
     * @param ollamaModel   待调用的模型名称
     * @param prompt        用户输入的提示词
     */
    public static String chat(String ollamaBaseUrl, String ollamaModel, String prompt) throws IOException {
        Objects.requireNonNull(ollamaBaseUrl, "ollamaBaseUrl");
        Objects.requireNonNull(ollamaModel, "ollamaModel");
        Objects.requireNonNull(prompt, "prompt");

        String responseJson = sendPrompt(ollamaBaseUrl, ollamaModel, prompt);
        JsonNode root = MAPPER.readTree(responseJson);
        JsonNode responseNode = root.get(RESPONSE_FIELD);
        if (responseNode == null || responseNode.isNull()) {
            throw new IOException("Ollama 响应中缺少 response 字段");

        }
        return responseNode.asText().trim();
    }


    private static String sendPrompt(String ollamaBaseUrl, String ollamaModel, String prompt) throws IOException {
        ObjectNode body = MAPPER.createObjectNode();
        body.put("model", ollamaModel);
        body.put("prompt", prompt);
        body.put("stream", false);
        URL url = new URL(normalizeBaseUrl(ollamaBaseUrl) + GENERATE_PATH);
        return HttpJsonClient.post(url, Collections.emptyMap(), body);
    }

    private static String normalizeBaseUrl(String baseUrl) {
        String trimmed = baseUrl.trim();
        if (trimmed.endsWith("/")) {
            return trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }
}
