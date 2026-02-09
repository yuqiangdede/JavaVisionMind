package com.yuqiangdede.ocr.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuqiangdede.ocr.dto.input.OcrDetectionRequest;
import com.yuqiangdede.ocr.dto.output.OcrDetectionResult;
import com.yuqiangdede.ocr.service.OcrService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OcrController.class)
class OcrControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private OcrService ocrService;

    @Test
    void detect_returnsResult() throws Exception {
        OcrDetectionResult result = new OcrDetectionResult(List.of(), "text", 0.9d);
        when(ocrService.detect(any(OcrDetectionRequest.class)))
                .thenReturn(List.of(result));

        mockMvc.perform(post("/api/v1/ocr/detect")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(writeJson(request())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }

    @Test
    void detectImage_returnsImage() throws Exception {
        when(ocrService.detectI(any(OcrDetectionRequest.class)))
                .thenReturn(new byte[]{1, 2, 3});

        mockMvc.perform(post("/api/v1/ocr/detectI")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(writeJson(request())))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_JPEG));
    }

    @Test
    void detectWithSR_returnsResult() throws Exception {
        when(ocrService.detectWithSR(any(OcrDetectionRequest.class)))
                .thenReturn("sr-result");

        mockMvc.perform(post("/api/v1/ocr/detectWithSR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(writeJson(request())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }

    @Test
    void detectWithLLM_returnsResult() throws Exception {
        when(ocrService.detectWithLLM(any(OcrDetectionRequest.class)))
                .thenReturn("llm-result");

        mockMvc.perform(post("/api/v1/ocr/detectWithLLM")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(writeJson(request())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }

    @Test
    void detectWithLLMI_returnsImage() throws Exception {
        when(ocrService.detectWithLLMI(any(OcrDetectionRequest.class)))
                .thenReturn(new byte[]{9, 8, 7});

        mockMvc.perform(post("/api/v1/ocr/detectWithLLMI")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(writeJson(request())))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_JPEG));
    }

    private OcrDetectionRequest request() {
        OcrDetectionRequest request = new OcrDetectionRequest();
        request.setImgUrl("http://example.com/ocr.jpg");
        return request;
    }

    private String writeJson(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}
