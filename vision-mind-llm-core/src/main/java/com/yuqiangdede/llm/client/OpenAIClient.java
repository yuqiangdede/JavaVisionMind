package com.yuqiangdede.llm.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yuqiangdede.llm.client.support.HttpJsonClient;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.URL;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;

@Slf4j
public final class OpenAIClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String HEADER_AUTHORIZATION = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private OpenAIClient() {
        throw new UnsupportedOperationException("Utility class");
    }


    /**
     * 调用 OpenAI Chat Completions 接口并返回纯文本回答。
     *
     * @param openaiBaseUrl OpenAI 接口地址
     * @param openaiKey     OpenAI API Key
     * @param openaiModel   目标模型名称
     * @param prompt        用户输入的提示词
     */
    public static String chat(String openaiBaseUrl, String openaiKey, String openaiModel, String prompt) throws IOException {
        Objects.requireNonNull(openaiBaseUrl, "openaiBaseUrl");
        Objects.requireNonNull(openaiKey, "openaiKey");
        Objects.requireNonNull(openaiModel, "openaiModel");
        Objects.requireNonNull(prompt, "prompt");

        String responseJson = sendPrompt(openaiBaseUrl, openaiKey, openaiModel, prompt);
        JsonNode root = MAPPER.readTree(responseJson);

        JsonNode contentNode = root.path("choices").path(0).path("message").path("content");
        if (contentNode.isMissingNode() || contentNode.isNull()) {
            throw new IOException("OpenAI 响应中缺少 message.content 字段");
        }
        return contentNode.asText().trim();
    }

    private static String sendPrompt(String openaiBaseUrl, String openaiKey, String openaiModel, String prompt) throws IOException {
        ObjectNode body = MAPPER.createObjectNode();
        body.put("model", openaiModel);

        ArrayNode messages = body.putArray("messages");
        ObjectNode messageNode = messages.addObject();
        messageNode.put("role", "user");
        messageNode.put("content", prompt);


        Map<String, String> headers = Collections.singletonMap(HEADER_AUTHORIZATION, BEARER_PREFIX + openaiKey);


        String normalizedUrl = openaiBaseUrl.trim();


        log.debug("OpenAIClient POST to: {}", normalizedUrl);
        log.debug("OpenAIClient Body: {}", body);

        URL url = new URL(normalizedUrl);
        String response = HttpJsonClient.post(url, headers, body);


        log.debug("OpenAIClient Response: {}", response);
        return response;
    }
}
