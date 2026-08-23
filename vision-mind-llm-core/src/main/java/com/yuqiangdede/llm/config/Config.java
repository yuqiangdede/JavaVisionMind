package com.yuqiangdede.llm.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
@Getter
@Setter
public class Config {

    @Value("${vision-mind.llm.ollama.base-url:}")
    private String ollamaBaseUrl;

    @Value("${vision-mind.llm.ollama.model:}")
    private String ollamaModel;

    @Value("${vision-mind.llm.openai.base-url:}")
    private String openaiBaseUrl;

    @Value("${vision-mind.llm.openai.api-key:}")
    private String openaiKey;

    @Value("${vision-mind.llm.openai.model:}")
    private String openaiModel;

    @Value("${vision-mind.llm.http-timeout-ms:100000}")
    private int httpTimeoutMs;

}
