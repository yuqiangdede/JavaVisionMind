package com.yuqiangdede.platform.starter.web.openapi;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfiguration {

    @Bean
    public OpenAPI visionMindOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("JavaVisionMind API")
                        .description("JavaVisionMind unified API document")
                        .version("v1"));
    }
}
