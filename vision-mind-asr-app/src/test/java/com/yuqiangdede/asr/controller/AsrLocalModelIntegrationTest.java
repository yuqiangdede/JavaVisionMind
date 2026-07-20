package com.yuqiangdede.asr.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuqiangdede.asr.AsrApplication;
import com.yuqiangdede.asr.dto.input.HotwordConfigUpdateRequest;
import com.yuqiangdede.asr.dto.input.PhraseRuleConfigUpdateRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = AsrApplication.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class AsrLocalModelIntegrationTest {

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
    void healthAndConfigurationEndpointsUseLocalRuntime() throws Exception {
        mockMvc.perform(get("/api/v1/asr/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.ready").value(true));

        mockMvc.perform(post("/api/v1/asr/hotwords")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new HotwordConfigUpdateRequest() {{
                            setBaseTerms(List.of("视觉检测", "JavaVisionMind"));
                        }})))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));

        PhraseRuleConfigUpdateRequest rules = new PhraseRuleConfigUpdateRequest();
        rules.setLines(List.of("视觉检测=>视觉检测"));
        mockMvc.perform(post("/api/v1/asr/phrase-rules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(rules)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }

    @Test
    void transcribeLocalWavAndBase64Source() throws Exception {
        byte[] wav = Files.readAllBytes(asset("asr-zh-sample.wav"));
        MockMultipartFile file = new MockMultipartFile("file", "sample.wav", "audio/wav", wav);

        mockMvc.perform(multipart("/api/v1/asr/transcribe").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.rawText").exists());

        String base64 = Base64.getEncoder().encodeToString(wav);
        String body = "{\"audioBase64\":\"" + base64 + "\",\"fileName\":\"sample.wav\"}";
        mockMvc.perform(post("/api/v1/asr/transcribe/source")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }

    private Path asset(String name) {
        Path current = Paths.get("test", "assets", name);
        if (Files.isRegularFile(current)) {
            return current;
        }
        return Paths.get("..", "test", "assets", name);
    }
}
