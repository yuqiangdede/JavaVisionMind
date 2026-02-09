package com.yuqiangdede.yolo.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuqiangdede.common.dto.output.Box;
import com.yuqiangdede.yolo.dto.input.DetectionRequest;
import com.yuqiangdede.yolo.dto.input.TextPromptRequestWithArea;
import com.yuqiangdede.yolo.dto.output.SegDetection;
import com.yuqiangdede.yolo.service.ImgAnalysisService;
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

@WebMvcTest(YoloTextController.class)
class YoloTextControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private ImgAnalysisService imgAnalysisService;

    @Test
    void detectText_returnsResult() throws Exception {
        when(imgAnalysisService.detectTextArea(any(TextPromptRequestWithArea.class)))
                .thenReturn(List.of(new Box(1, 2, 3, 4)));

        mockMvc.perform(post("/api/v1/yoloe/detectText")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(writeJson(textRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }

    @Test
    void detectTextI_returnsImage() throws Exception {
        when(imgAnalysisService.detectTextAreaI(any(TextPromptRequestWithArea.class)))
                .thenReturn(sampleImage());

        mockMvc.perform(post("/api/v1/yoloe/detectTextI")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(writeJson(textRequest())))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_JPEG));
    }

    @Test
    void detectFree_returnsResult() throws Exception {
        SegDetection segDetection = new SegDetection();
        segDetection.setScore(0.8f);
        segDetection.setClassId(1);
        segDetection.setClassName("seg");
        when(imgAnalysisService.detectFree(any(DetectionRequest.class)))
                .thenReturn(List.of(segDetection));

        mockMvc.perform(post("/api/v1/yoloe/detectFree")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(writeJson(basicRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }

    @Test
    void detectFreeI_returnsImage() throws Exception {
        when(imgAnalysisService.detectFreeI(any(DetectionRequest.class)))
                .thenReturn(sampleImage());

        mockMvc.perform(post("/api/v1/yoloe/detectFreeI")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(writeJson(basicRequest())))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_JPEG));
    }

    private TextPromptRequestWithArea textRequest() {
        TextPromptRequestWithArea request = new TextPromptRequestWithArea();
        request.setImgUrl("http://example.com/text.jpg");
        return request;
    }

    private DetectionRequest basicRequest() {
        DetectionRequest request = new DetectionRequest();
        request.setImgUrl("http://example.com/free.jpg");
        return request;
    }

    private BufferedImage sampleImage() {
        return new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
    }

    private String writeJson(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}
