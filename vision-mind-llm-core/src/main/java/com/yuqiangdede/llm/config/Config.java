package com.yuqiangdede.llm.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@PropertySource(value = "classpath:llm-defaults.properties", ignoreResourceNotFound = false)
@Getter
@Setter
public class Config {

    @Value("${ollama.base-url:}")
    private String ollamaBaseUrl;

    @Value("${ollama.chat.options.model:}")
    private String ollamaModel;

    @Value("${openai.base-url:}")
    private String openaiBaseUrl;

    @Value("${openai.api-key:}")
    private String openaiKey;

    @Value("${openai.chat.options.model:}")
    private String openaiModel;

    @Value("${llm.http-timeout-ms:100000}")
    private int httpTimeoutMs;

}
