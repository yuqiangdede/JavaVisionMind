package com.yuqiangdede.llm.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yuqiangdede.llm.client.support.HttpJsonClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URI;
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
     * 调用 OpenAI Chat Completions 接口，返回纯文本结果。
     */
    public static String chat(String openaiBaseUrl,
                              String openaiKey,
                              String openaiModel,
                              String prompt) throws IOException {
        return chat(openaiBaseUrl, openaiKey, openaiModel, prompt, 0);
    }

    public static String chat(String openaiBaseUrl,
                              String openaiKey,
                              String openaiModel,
                              String prompt,
                              int timeoutMs) throws IOException {
        Objects.requireNonNull(openaiBaseUrl, "openaiBaseUrl");
        Objects.requireNonNull(openaiKey, "openaiKey");
        Objects.requireNonNull(openaiModel, "openaiModel");
        Objects.requireNonNull(prompt, "prompt");

        String responseJson = sendPrompt(openaiBaseUrl, openaiKey, openaiModel, prompt, timeoutMs);
        JsonNode root = MAPPER.readTree(responseJson);

        JsonNode contentNode = root.path("choices").path(0).path("message").path("content");
        if (contentNode.isMissingNode() || contentNode.isNull()) {
            throw new IOException("OpenAI 响应缺少 message.content 字段");
        }
        return contentNode.asText().trim();
    }

    public static String chatWithImage(String openaiBaseUrl,
                                       String openaiKey,
                                       String openaiModel,
                                       String systemPrompt,
                                       String textPrompt,
                                       String imageUrl) throws IOException {
        return chatWithImage(openaiBaseUrl, openaiKey, openaiModel, systemPrompt, textPrompt, imageUrl, 0);
    }

    public static String chatWithImage(String openaiBaseUrl,
                                       String openaiKey,
                                       String openaiModel,
                                       String systemPrompt,
                                       String textPrompt,
                                       String imageUrl,
                                       int timeoutMs) throws IOException {
        Objects.requireNonNull(openaiBaseUrl, "openaiBaseUrl");
        Objects.requireNonNull(openaiKey, "openaiKey");
        Objects.requireNonNull(openaiModel, "openaiModel");

        if (!StringUtils.hasText(textPrompt) && !StringUtils.hasText(imageUrl)) {
            throw new IllegalArgumentException("图文对话需要提供文本或图片信息");
        }

        String responseJson = sendMultimodalPrompt(openaiBaseUrl, openaiKey, openaiModel, systemPrompt, textPrompt, imageUrl, timeoutMs);
        JsonNode root = MAPPER.readTree(responseJson);
        JsonNode contentNode = root.path("choices").path(0).path("message").path("content");
        if (contentNode.isMissingNode() || contentNode.isNull()) {
            throw new IOException("OpenAI 响应缺少 message.content 字段");
        }
        return contentNode.asText().trim();
    }

    private static String sendPrompt(String openaiBaseUrl,
                                     String openaiKey,
                                     String openaiModel,
                                     String prompt,
                                     int timeoutMs) throws IOException {
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

        URL url = URI.create(normalizedUrl).toURL();
        String response = HttpJsonClient.post(url, headers, body, timeoutMs);

        log.debug("OpenAIClient Response: {}", response);
        return response;
    }

    private static String sendMultimodalPrompt(String openaiBaseUrl,
                                               String openaiKey,
                                               String openaiModel,
                                               String systemPrompt,
                                               String textPrompt,
                                               String imageUrl,
                                               int timeoutMs) throws IOException {
        ObjectNode body = MAPPER.createObjectNode();
        body.put("model", openaiModel);

        ArrayNode messages = body.putArray("messages");
        if (StringUtils.hasText(systemPrompt)) {
            ObjectNode systemNode = messages.addObject();
            systemNode.put("role", "system");
            systemNode.put("content", systemPrompt);
        }

        ObjectNode userNode = messages.addObject();
        userNode.put("role", "user");
        ArrayNode userContent = userNode.putArray("content");
        if (StringUtils.hasText(textPrompt)) {
            ObjectNode textNode = userContent.addObject();
            textNode.put("type", "text");
            textNode.put("text", textPrompt);
        }
        if (StringUtils.hasText(imageUrl)) {
            ObjectNode imageNode = userContent.addObject();
            imageNode.put("type", "image_url");
            ObjectNode imageObject = imageNode.putObject("image_url");
            imageObject.put("url", imageUrl);
        }

        body.put("stream", false);

        Map<String, String> headers = Collections.singletonMap(HEADER_AUTHORIZATION, BEARER_PREFIX + openaiKey);

        String normalizedUrl = openaiBaseUrl.trim();

        log.debug("OpenAIClient multimodal POST to: {}", normalizedUrl);
        log.debug("OpenAIClient multimodal Body: {}", body);

        URL url = URI.create(normalizedUrl).toURL();
        String response = HttpJsonClient.post(url, headers, body, timeoutMs);

        log.debug("OpenAIClient multimodal Response: {}", response);
        return response;
    }
}
