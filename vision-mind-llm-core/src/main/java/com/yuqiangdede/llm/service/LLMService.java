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
        // 聊天流程遵循“优先外部云端、其次本地”原则：
        // 1. 只要配置了 OpenAI 的地址、密钥和模型，就调用云端服务。
        if (isNotBlank(config.getOpenaiBaseUrl()) &&
                isNotBlank(config.getOpenaiKey()) &&
                isNotBlank(config.getOpenaiModel())) {
            return OpenAIClient.chat(config.getOpenaiBaseUrl(), config.getOpenaiKey(), config.getOpenaiModel(), message);
        }

        // 2. 未配置 OpenAI 时回退到 Ollama，本地推理无需 API Key。
        if (isNotBlank(config.getOllamaBaseUrl()) &&
                isNotBlank(config.getOllamaModel())) {
            return OllamaClient.chat(config.getOllamaBaseUrl(), config.getOllamaModel(), message);
        }

        // 3. 两者都未配置时视为环境未准备好，直接抛出异常提醒使用者。
        throw new IllegalStateException("未配置 OpenAI 或 Ollama，无法对话");
    }

    public String chatWithImg(String message, String img) {
        // TODO: 预留图文多模态调用能力，后续接入具备图像理解能力的模型再补充实现。
        return null;
    }
}
