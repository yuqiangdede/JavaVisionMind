package com.yuqiangdede.yolo.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuqiangdede.yolo.YoloApplication;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = YoloApplication.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ContextConfiguration(initializers = YoloLocalModelIntegrationTest.NativeRuntimeInitializer.class)
@AutoConfigureMockMvc
class YoloLocalModelIntegrationTest {

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
    void detectPoseAndSegmentationUseLocalModels() throws Exception {
        String image = asset("car-electric.jpg").toUri().toString();
        String request = objectMapper.writeValueAsString(Map.of("imgUrl", image));

        mockMvc.perform(post("/api/v1/img/detect")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data").isArray());

        mockMvc.perform(post("/api/v1/img/pose")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));

        mockMvc.perform(post("/api/v1/img/detectI")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_JPEG));
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
