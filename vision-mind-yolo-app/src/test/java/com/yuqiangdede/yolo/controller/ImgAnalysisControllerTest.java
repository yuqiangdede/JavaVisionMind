package com.yuqiangdede.yolo.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuqiangdede.common.dto.Point;
import com.yuqiangdede.common.dto.output.Box;
import com.yuqiangdede.common.dto.output.BoxWithKeypoints;
import com.yuqiangdede.yolo.dto.input.DetectionRequest;
import com.yuqiangdede.yolo.dto.input.DetectionRequestWithArea;
import com.yuqiangdede.yolo.dto.output.SegDetection;
import com.yuqiangdede.yolo.service.ImgAnalysisService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ImgAnalysisController.class)
class ImgAnalysisControllerTest {

    static {
        System.setProperty("vision-mind.skip-opencv", "true");
    }

    @AfterAll
    static void clearSkipFlag() {
        System.clearProperty("vision-mind.skip-opencv");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ImgAnalysisService imgAnalysisService;

    @Test
    void predictorArea_returnsBoxesAndSuccessCode() throws Exception {
        DetectionRequestWithArea request = buildDetectionRequestWithArea();
        List<Box> response = List.of(new Box(1f, 2f, 3f, 4f));
        Mockito.when(imgAnalysisService.detectArea(any(DetectionRequestWithArea.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/img/detect")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJson(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data[0].x1").value(1.0));

        verify(imgAnalysisService).detectArea(any(DetectionRequestWithArea.class));
    }

    @Test
    void predictorFace_returnsFacesAndSuccessCode() throws Exception {
        DetectionRequestWithArea request = buildDetectionRequestWithArea();
        List<Box> response = List.of(new Box(5f, 6f, 7f, 8f));
        Mockito.when(imgAnalysisService.detectFace(any(DetectionRequestWithArea.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/img/detectFace")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJson(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data[0].x1").value(5.0));

        verify(imgAnalysisService).detectFace(any(DetectionRequestWithArea.class));
    }

    @Test
    void poseArea_returnsKeyPointsAndSuccessCode() throws Exception {
        DetectionRequestWithArea request = buildDetectionRequestWithArea();
        BoxWithKeypoints poseBox = new BoxWithKeypoints(1f, 2f, 3f, 4f, 0.9f);
        poseBox.setKeypoints(List.of(new Point(10f, 20f)));
        Mockito.when(imgAnalysisService.poseArea(any(DetectionRequestWithArea.class))).thenReturn(List.of(poseBox));

        mockMvc.perform(post("/api/v1/img/pose")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJson(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data[0].keypoints[0].x").value(10.0));

        verify(imgAnalysisService).poseArea(any(DetectionRequestWithArea.class));
    }

    @Test
    void segArea_returnsSegmentationsAndSuccessCode() throws Exception {
        DetectionRequestWithArea request = buildDetectionRequestWithArea();
        Mockito.when(imgAnalysisService.segArea(any(DetectionRequestWithArea.class))).thenReturn(List.of(new SegDetection()));

        mockMvc.perform(post("/api/v1/img/seg")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJson(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));

        verify(imgAnalysisService).segArea(any(DetectionRequestWithArea.class));
    }

    @Test
    void samArea_returnsBoxesAndSuccessCode() throws Exception {
        DetectionRequest request = buildDetectionRequest();
        List<Box> response = List.of(new Box(9f, 10f, 11f, 12f));
        Mockito.when(imgAnalysisService.sam(any(DetectionRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/img/sam")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJson(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data[0].x1").value(9.0));

        verify(imgAnalysisService).sam(any(DetectionRequest.class));
    }

    @Test
    void predictorAreaI_returnsImageResponse() throws Exception {
        DetectionRequestWithArea request = buildDetectionRequestWithArea();
        Mockito.when(imgAnalysisService.detectAreaI(any(DetectionRequestWithArea.class))).thenReturn(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB));

        mockMvc.perform(post("/api/v1/img/detectI")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJson(request)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_JPEG))
                .andExpect(result -> assertThat(result.getResponse().getContentAsByteArray()).isNotEmpty());

        verify(imgAnalysisService).detectAreaI(any(DetectionRequestWithArea.class));
    }

    @Test
    void detectFaceI_returnsImageResponse() throws Exception {
        DetectionRequestWithArea request = buildDetectionRequestWithArea();
        Mockito.when(imgAnalysisService.detectFaceI(any(DetectionRequestWithArea.class))).thenReturn(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB));

        mockMvc.perform(post("/api/v1/img/detectFaceI")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJson(request)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_JPEG))
                .andExpect(result -> assertThat(result.getResponse().getContentAsByteArray()).isNotEmpty());

        verify(imgAnalysisService).detectFaceI(any(DetectionRequestWithArea.class));
    }

    @Test
    void poseAreaI_returnsImageResponse() throws Exception {
        DetectionRequestWithArea request = buildDetectionRequestWithArea();
        Mockito.when(imgAnalysisService.poseAreaI(any(DetectionRequestWithArea.class))).thenReturn(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB));

        mockMvc.perform(post("/api/v1/img/poseI")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJson(request)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_JPEG))
                .andExpect(result -> assertThat(result.getResponse().getContentAsByteArray()).isNotEmpty());

        verify(imgAnalysisService).poseAreaI(any(DetectionRequestWithArea.class));
    }

    @Test
    void segAreaI_returnsImageResponse() throws Exception {
        DetectionRequestWithArea request = buildDetectionRequestWithArea();
        Mockito.when(imgAnalysisService.segAreaI(any(DetectionRequestWithArea.class))).thenReturn(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB));

        mockMvc.perform(post("/api/v1/img/segI")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJson(request)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_JPEG))
                .andExpect(result -> assertThat(result.getResponse().getContentAsByteArray()).isNotEmpty());

        verify(imgAnalysisService).segAreaI(any(DetectionRequestWithArea.class));
    }

    @Test
    void samI_returnsImageResponse() throws Exception {
        DetectionRequest request = buildDetectionRequest();
        Mockito.when(imgAnalysisService.samI(any(DetectionRequest.class))).thenReturn(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB));

        mockMvc.perform(post("/api/v1/img/samI")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJson(request)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_JPEG))
                .andExpect(result -> assertThat(result.getResponse().getContentAsByteArray()).isNotEmpty());

        verify(imgAnalysisService).samI(any(DetectionRequest.class));
    }

    private DetectionRequestWithArea buildDetectionRequestWithArea() {
        DetectionRequestWithArea request = new DetectionRequestWithArea();
        request.setImgUrl("https://example.com/test.jpg");
        request.setThreshold(0.2f);
        request.setTypes("0,1,2");

        ArrayList<Point> frame = new ArrayList<>();
        frame.add(new Point(10f, 10f));
        frame.add(new Point(100f, 10f));
        frame.add(new Point(100f, 100f));
        frame.add(new Point(10f, 100f));

        ArrayList<ArrayList<Point>> frames = new ArrayList<>();
        frames.add(frame);
        request.setDetectionFrames(frames);
        request.setBlockingFrames(new ArrayList<>());
        return request;
    }

    private DetectionRequest buildDetectionRequest() {
        return new DetectionRequest("https://example.com/test.jpg", 0.4f, "1,2");
    }

    private String asJson(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}
