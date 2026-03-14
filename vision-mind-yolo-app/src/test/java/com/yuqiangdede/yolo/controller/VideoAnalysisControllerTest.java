package com.yuqiangdede.yolo.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuqiangdede.common.dto.output.Box;
import com.yuqiangdede.yolo.dto.input.VideoInput;
import com.yuqiangdede.yolo.dto.output.VideoFrameDetectionResult;
import com.yuqiangdede.yolo.service.VideoAnalysisService;
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
    void videoPredictor_returnsResult() throws Exception {
        VideoFrameDetectionResult frame = new VideoFrameDetectionResult(
                5,
                160L,
                12L,
                List.of(new Box(1, 2, 3, 4))
        );
        when(videoAnalysisService.detect(any(VideoInput.class))).thenReturn(List.of(frame));

        mockMvc.perform(post("/api/v1/video/detect")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(writeJson(validRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data[0].frameIndex").value(5));
    }

    @Test
    void videoPredictor_missingRtspUrl_returnsError() throws Exception {
        VideoInput request = new VideoInput();
        request.setFrameNum(10);

        mockMvc.perform(post("/api/v1/video/detect")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(writeJson(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("-1"))
                .andExpect(jsonPath("$.msg").value("rtspUrl is null or empty"));
    }

    @Test
    void videoPredictor_blankRtspUrl_returnsError() throws Exception {
        VideoInput request = new VideoInput();
        request.setRtspUrl("   ");
        request.setFrameNum(10);

        mockMvc.perform(post("/api/v1/video/detect")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(writeJson(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("-1"))
                .andExpect(jsonPath("$.msg").value("rtspUrl is null or empty"));
    }

    private VideoInput validRequest() {
        VideoInput request = new VideoInput();
        request.setRtspUrl("rtsp://example.com/live");
        request.setFrameNum(10);
        request.setFrameInterval(2);
        return request;
    }

    private String writeJson(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}
