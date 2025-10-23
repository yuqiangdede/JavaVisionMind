package com.yuqiangdede.llm.service;

import com.yuqiangdede.llm.client.OllamaClient;
import com.yuqiangdede.llm.client.OpenAIClient;
import com.yuqiangdede.llm.config.Config;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;

@Service
@Slf4j
public class LLMService {

    private final Config config;

    public LLMService(Config config) {
        this.config = config;
    }

    public String chat(String message) throws IOException {

        if (!StringUtils.hasText(message)) {
            throw new IllegalArgumentException("对话内容不能为空");
        }

        int timeoutMs = config.getHttpTimeoutMs();

        if (isConfigured(config.getOpenaiBaseUrl()) &&
                isConfigured(config.getOpenaiKey()) &&
                isConfigured(config.getOpenaiModel())) {

            return OpenAIClient.chat(
                    config.getOpenaiBaseUrl(),
                    config.getOpenaiKey(),
                    config.getOpenaiModel(),
                    message,
                    timeoutMs
            );
        }

        if (isConfigured(config.getOllamaBaseUrl()) &&
                isConfigured(config.getOllamaModel())) {

            return OllamaClient.chat(
                    config.getOllamaBaseUrl(),
                    config.getOllamaModel(),
                    message,
                    timeoutMs
            );
        }

        throw new IllegalStateException("未配置 OpenAI 或 Ollama，无法对话");
    }

    public String chatWithImg(String message, String imageUrl, String systemPrompt) throws IOException {
        if (!StringUtils.hasText(message) && !StringUtils.hasText(imageUrl)) {
            throw new IllegalArgumentException("图文对话需要提供文本或图片");
        }

        int timeoutMs = config.getHttpTimeoutMs();

        if (isConfigured(config.getOpenaiBaseUrl()) &&
                isConfigured(config.getOpenaiKey()) &&
                isConfigured(config.getOpenaiModel())) {
            String effectiveSystem = StringUtils.hasText(systemPrompt)
                    ? systemPrompt
                    : "请使用中文回答我的问题。";
            return OpenAIClient.chatWithImage(
                    config.getOpenaiBaseUrl(),
                    config.getOpenaiKey(),
                    config.getOpenaiModel(),
                    effectiveSystem,
                    message,
                    imageUrl,
                    timeoutMs
            );
        }

        if (isConfigured(config.getOllamaBaseUrl()) &&
                isConfigured(config.getOllamaModel())) {
            String prompt = buildOllamaPrompt(message, systemPrompt);
            return OllamaClient.chatWithImage(
                    config.getOllamaBaseUrl(),
                    config.getOllamaModel(),
                    prompt,
                    imageUrl,
                    timeoutMs
            );
        }

        throw new IllegalStateException("未配置支持图像输入的大模型服务，无法处理图文对话");
    }

    private boolean isConfigured(String value) {
        return StringUtils.hasText(value);
    }

    private String buildOllamaPrompt(String message, String systemPrompt) {
        StringBuilder builder = new StringBuilder();
        if (StringUtils.hasText(systemPrompt)) {
            builder.append(systemPrompt.trim());
        }
        if (StringUtils.hasText(message)) {
            if (builder.length() > 0) {
                builder.append("\n\n");
            }
            builder.append(message.trim());
        }
        if (builder.length() == 0) {
            builder.append("请描述这张图片。");
        }
        return builder.toString();
    }
}
