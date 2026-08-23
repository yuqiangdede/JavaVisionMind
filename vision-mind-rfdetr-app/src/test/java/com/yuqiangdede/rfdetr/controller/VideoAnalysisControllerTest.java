package com.yuqiangdede.rfdetr.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuqiangdede.common.dto.output.Box;
import com.yuqiangdede.rfdetr.dto.input.VideoInput;
import com.yuqiangdede.rfdetr.dto.output.VideoFrameDetectionResult;
import com.yuqiangdede.rfdetr.service.VideoAnalysisService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(VideoAnalysisController.class)
class VideoAnalysisControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private VideoAnalysisService videoAnalysisService;

    @Test
    void detect_supportsUnifiedAndCompatibilityRoutes() throws Exception {
        when(videoAnalysisService.detect(any(VideoInput.class))).thenReturn(List.of(
                new VideoFrameDetectionResult(5, 160L, 12L, List.of(new Box(1, 2, 3, 4)))
        ));
        for (String route : List.of("/api/v1/vision/video/detect", "/api/v1/video/detect")) {
            mockMvc.perform(post(route).contentType(MediaType.APPLICATION_JSON).content(json(validRequest())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value("0"))
                    .andExpect(jsonPath("$.data[0].frameIndex").value(5));
        }
    }

    @Test
    void detect_missingRtspUrl_returnsCompatibilityError() throws Exception {
        mockMvc.perform(post("/api/v1/vision/video/detect").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("-1"))
                .andExpect(jsonPath("$.msg").value("rtspUrl is null or empty"));
    }

    private VideoInput validRequest() {
        VideoInput input = new VideoInput();
        input.setRtspUrl("rtsp://example.com/live");
        return input;
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}
