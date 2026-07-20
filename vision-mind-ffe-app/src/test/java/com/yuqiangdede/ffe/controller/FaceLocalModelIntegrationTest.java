package com.yuqiangdede.ffe.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuqiangdede.ffe.FfeApplication;
import com.yuqiangdede.ffe.dto.input.InputWithUrl;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = FfeApplication.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ContextConfiguration(initializers = FaceLocalModelIntegrationTest.NativeRuntimeInitializer.class)
@AutoConfigureMockMvc
class FaceLocalModelIntegrationTest {

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
    void computeFaceVectorUsesLocalFacePipeline() throws Exception {
        String image = asset("china-street-sign.jpg").toUri().toString();
        mockMvc.perform(post("/api/v1/face/computeFaceVector")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new InputWithUrl(image, null, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
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
