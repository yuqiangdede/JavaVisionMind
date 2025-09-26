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

        // 聊天流程遵循“优先外部云端、其次本地”原则：
        // 1. 只要配置了 OpenAI 的地址、密钥和模型，就调用云端服务。
        if (isConfigured(config.getOpenaiBaseUrl()) &&
                isConfigured(config.getOpenaiKey()) &&
                isConfigured(config.getOpenaiModel())) {

            return OpenAIClient.chat(config.getOpenaiBaseUrl(), config.getOpenaiKey(), config.getOpenaiModel(), message);
        }

        // 2. 未配置 OpenAI 时回退到 Ollama，本地推理无需 API Key。

        if (isConfigured(config.getOllamaBaseUrl()) &&
                isConfigured(config.getOllamaModel())) {

            return OllamaClient.chat(config.getOllamaBaseUrl(), config.getOllamaModel(), message);
        }

        // 3. 两者都未配置时视为环境未准备好，直接抛出异常提醒使用者。
        throw new IllegalStateException("未配置 OpenAI 或 Ollama，无法对话");
    }

    public String chatWithImg(String message, String imageUrl, String systemPrompt) throws IOException {
        if (!StringUtils.hasText(message) && !StringUtils.hasText(imageUrl)) {
            throw new IllegalArgumentException("图文对话需要提供文本或图片");
        }

        if (isConfigured(config.getOpenaiBaseUrl()) &&
                isConfigured(config.getOpenaiKey()) &&
                isConfigured(config.getOpenaiModel())) {
            String effectiveSystem = StringUtils.hasText(systemPrompt)
                    ? systemPrompt
                    : "请使用中文回答问题.";
            return OpenAIClient.chatWithImage(
                    config.getOpenaiBaseUrl(),
                    config.getOpenaiKey(),
                    config.getOpenaiModel(),
                    effectiveSystem,
                    message,
                    imageUrl
            );
        }

        throw new IllegalStateException("未配置支持图像的 OpenAI 服务，无法进行图文对话");
    }

    private boolean isConfigured(String value) {
        return StringUtils.hasText(value);
    }
}
