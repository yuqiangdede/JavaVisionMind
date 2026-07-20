package com.yuqiangdede.reid.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuqiangdede.reid.ReidApplication;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = ReidApplication.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ContextConfiguration(initializers = ReidLocalModelIntegrationTest.NativeRuntimeInitializer.class)
@AutoConfigureMockMvc
class ReidLocalModelIntegrationTest {

    private static final String INTEGRATION_TEST = "vision-mind.integration-test";

    static {
        System.setProperty(INTEGRATION_TEST, "true");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @AfterAll
    static void disableNativeRuntime() {
        System.clearProperty(INTEGRATION_TEST);
    }

    @Test
    void featureAndSimilarityUseLocalPersonImage() throws Exception {
        String image = asset("car-electric.jpg").toUri().toString();
        mockMvc.perform(post("/api/v1/reid/feature/single")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("imgUrl", image))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));

        mockMvc.perform(post("/api/v1/reid/feature/calculateSimilarity")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "imgUrl1", image,
                                "imgUrl2", image))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data").isNumber());
    }

    private Path asset(String name) {
        Path current = Paths.get("test", "assets", name);
        return Files.isRegularFile(current) ? current : Paths.get("..", "test", "assets", name);
    }

    static class NativeRuntimeInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        @Override
        public void initialize(ConfigurableApplicationContext applicationContext) {
            System.setProperty(INTEGRATION_TEST, "true");
        }
    }
}
