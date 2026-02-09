package com.yuqiangdede.lpr.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuqiangdede.common.dto.output.Box;
import com.yuqiangdede.lpr.controller.dto.PlateRecognitionResult;
import com.yuqiangdede.lpr.service.LprService;
import com.yuqiangdede.yolo.dto.input.DetectionRequestWithArea;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.awt.image.BufferedImage;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LprController.class)
class LprControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private LprService lprService;

    @Test
    void recognize_returnsResult() throws Exception {
        when(lprService.analyze(any(DetectionRequestWithArea.class)))
                .thenReturn(analysisResult());

        mockMvc.perform(post("/api/v1/lpr")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(writeJson(request())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }

    @Test
    void recognizeWithImage_returnsImage() throws Exception {
        when(lprService.analyze(any(DetectionRequestWithArea.class)))
                .thenReturn(analysisResult());
        when(lprService.overlay(any(LprService.AnalysisResult.class)))
                .thenReturn(sampleImage());

        mockMvc.perform(post("/api/v1/lprI")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(writeJson(request())))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_JPEG));
    }

    @Test
    void recognizeWithOcr_returnsResult() throws Exception {
        when(lprService.analyzeWithOcr(any(DetectionRequestWithArea.class)))
                .thenReturn(analysisResult());

        mockMvc.perform(post("/api/v1/lprOcr")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(writeJson(request())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }

    @Test
    void recognizeWithOcrImage_returnsImage() throws Exception {
        when(lprService.analyzeWithOcr(any(DetectionRequestWithArea.class)))
                .thenReturn(analysisResult());
        when(lprService.overlay(any(LprService.AnalysisResult.class)))
                .thenReturn(sampleImage());

        mockMvc.perform(post("/api/v1/lprOcrI")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(writeJson(request())))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_JPEG));
    }

    private DetectionRequestWithArea request() {
        DetectionRequestWithArea request = new DetectionRequestWithArea();
        request.setImgUrl("http://example.com/lpr.jpg");
        return request;
    }

    private LprService.AnalysisResult analysisResult() {
        PlateRecognitionResult result = new PlateRecognitionResult(new Box(1, 2, 3, 4), "ABC123");
        return new LprService.AnalysisResult(sampleImage(), List.of(result), List.of());
    }

    private BufferedImage sampleImage() {
        return new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
    }

    private String writeJson(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}
