package com.yuqiangdede.llm.service;

import com.yuqiangdede.llm.client.OllamaClient;
import com.yuqiangdede.llm.client.OpenAIClient;
import com.yuqiangdede.llm.config.Config;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
@Slf4j
public class LLMService {

    private final Config config;

    public LLMService(Config config) {
        this.config = config;
    }

    private boolean isNotBlank(String value) {
        return value != null && !value.trim().isEmpty();
    }

    public String chat(String message) throws IOException {
        if (isNotBlank(config.getOpenaiBaseUrl()) &&
                isNotBlank(config.getOpenaiKey()) &&
                isNotBlank(config.getOpenaiModel())) {
            return OpenAIClient.chat(config.getOpenaiBaseUrl(), config.getOpenaiKey(), config.getOpenaiModel(), message);
        }

        if (isNotBlank(config.getOllamaBaseUrl()) &&
                isNotBlank(config.getOllamaModel())) {
            return OllamaClient.chat(config.getOllamaBaseUrl(), config.getOllamaModel(), message);
        }

        throw new IllegalStateException("未配置 OpenAI 或 Ollama，无法对话");
    }

    public String chatWithImg(String message, String img) {
        return null;
    }
}
