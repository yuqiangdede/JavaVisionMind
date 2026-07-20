package com.yuqiangdede.tts.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuqiangdede.tts.TtsApplication;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = TtsApplication.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class TtsLocalModelIntegrationTest {

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
    void healthAndVoicesLoadLocalModel() throws Exception {
        mockMvc.perform(get("/api/v1/tts/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.ready").value(true));

        mockMvc.perform(get("/api/v1/tts/voices"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.voices").isArray());
    }

    @Test
    void synthesizeReturnsWavBytes() throws Exception {
        mockMvc.perform(post("/api/v1/tts/synthesize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "text", "这是本地语音合成测试。",
                                "voice", 0,
                                "speed", 1.0))))
                .andExpect(status().isOk())
                .andExpect(content().contentType("audio/wav"))
                .andExpect(header().exists("X-TTS-Model"))
                .andExpect(header().exists("X-TTS-Sample-Rate"))
                .andExpect(result -> org.junit.jupiter.api.Assertions.assertTrue(
                        result.getResponse().getContentAsByteArray().length > 44));
    }
}
