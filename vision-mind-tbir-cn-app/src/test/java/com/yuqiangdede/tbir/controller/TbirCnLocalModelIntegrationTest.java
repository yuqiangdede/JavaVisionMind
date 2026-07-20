package com.yuqiangdede.tbir.controller;

import com.yuqiangdede.tbir.TbirCnApplication;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = TbirCnApplication.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ContextConfiguration(initializers = TbirCnLocalModelIntegrationTest.NativeRuntimeInitializer.class)
@AutoConfigureMockMvc
class TbirCnLocalModelIntegrationTest {

    private static final String INTEGRATION_TEST = "vision-mind.integration-test";

    static {
        System.setProperty(INTEGRATION_TEST, "true");
    }

    @Autowired
    private MockMvc mockMvc;

    @AfterAll
    static void disableNativeRuntime() {
        System.clearProperty(INTEGRATION_TEST);
    }

    @Test
    void textImageSimilarityUsesLocalChineseClipModels() throws Exception {
        MockMultipartFile image = image("china-street-sign.jpg");
        mockMvc.perform(multipart("/api/v1/tbir/similarity/text-image")
                        .file(image)
                        .param("text", "中国街道标志"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.similarity").isNumber());
    }

    private MockMultipartFile image(String name) throws IOException {
        return new MockMultipartFile("image", name, MediaType.IMAGE_JPEG_VALUE,
                Files.readAllBytes(asset(name)));
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
