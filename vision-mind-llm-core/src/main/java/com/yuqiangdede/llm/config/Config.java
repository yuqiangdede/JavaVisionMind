package com.yuqiangdede.llm.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Getter
@Setter
public class Config {

    @Value("${ollama.base-url}")
    private String ollamaBaseUrl;

    @Value("${ollama.chat.options.model}")
    private String ollamaModel;

    @Value("${openai.base-url}")
    private String openaiBaseUrl;

    @Value("${openai.api-key}")
    private String openaiKey;

    @Value("${openai.chat.options.model}")
    private String openaiModel;

}
