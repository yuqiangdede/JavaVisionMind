package com.yuqiangdede.yolo.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuqiangdede.common.dto.output.Box;
import com.yuqiangdede.common.dto.output.BoxWithKeypoints;
import com.yuqiangdede.yolo.dto.input.DetectionRequest;
import com.yuqiangdede.yolo.dto.input.DetectionRequestWithArea;
import com.yuqiangdede.yolo.dto.output.ObbDetection;
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

@WebMvcTest(ImgAnalysisController.class)
class ImgAnalysisControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private ImgAnalysisService imgAnalysisService;

    @Test
    void predictorArea_returnsResult() throws Exception {
        when(imgAnalysisService.detectArea(any(DetectionRequestWithArea.class)))
                .thenReturn(List.of(new Box(1, 2, 3, 4)));

        mockMvc.perform(post("/api/v1/img/detect")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(writeJson(areaRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }

    @Test
    void predictorAreaI_returnsImage() throws Exception {
        when(imgAnalysisService.detectAreaI(any(DetectionRequestWithArea.class)))
                .thenReturn(sampleImage());

        mockMvc.perform(post("/api/v1/img/detectI")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(writeJson(areaRequest())))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_JPEG));
    }

    @Test
    void predictorFace_returnsResult() throws Exception {
        when(imgAnalysisService.detectFace(any(DetectionRequestWithArea.class)))
                .thenReturn(List.of(new Box(1, 2, 3, 4)));

        mockMvc.perform(post("/api/v1/img/detectFace")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(writeJson(areaRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }

    @Test
    void predictorFaceI_returnsImage() throws Exception {
        when(imgAnalysisService.detectFaceI(any(DetectionRequestWithArea.class)))
                .thenReturn(sampleImage());

        mockMvc.perform(post("/api/v1/img/detectFaceI")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(writeJson(areaRequest())))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_JPEG));
    }

    @Test
    void detectLicensePlate_returnsResult() throws Exception {
        when(imgAnalysisService.detectLP(any(DetectionRequestWithArea.class)))
                .thenReturn(List.of(new Box(1, 2, 3, 4)));

        mockMvc.perform(post("/api/v1/img/detectLP")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(writeJson(areaRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }

    @Test
    void detectLicensePlateI_returnsImage() throws Exception {
        when(imgAnalysisService.detectLPI(any(DetectionRequestWithArea.class)))
                .thenReturn(sampleImage());

        mockMvc.perform(post("/api/v1/img/detectLPI")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(writeJson(areaRequest())))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_JPEG));
    }

    @Test
    void poseArea_returnsResult() throws Exception {
        when(imgAnalysisService.poseArea(any(DetectionRequestWithArea.class)))
                .thenReturn(List.of(new BoxWithKeypoints(1f, 1f, 2f, 2f, 0.9f)));

        mockMvc.perform(post("/api/v1/img/pose")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(writeJson(areaRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }

    @Test
    void poseAreaI_returnsImage() throws Exception {
        when(imgAnalysisService.poseAreaI(any(DetectionRequestWithArea.class)))
                .thenReturn(sampleImage());

        mockMvc.perform(post("/api/v1/img/poseI")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(writeJson(areaRequest())))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_JPEG));
    }

    @Test
    void samArea_returnsResult() throws Exception {
        when(imgAnalysisService.sam(any(DetectionRequest.class)))
                .thenReturn(List.of(new Box(1, 2, 3, 4)));

        mockMvc.perform(post("/api/v1/img/sam")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(writeJson(basicRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }

    @Test
    void samI_returnsImage() throws Exception {
        when(imgAnalysisService.samI(any(DetectionRequest.class)))
                .thenReturn(sampleImage());

        mockMvc.perform(post("/api/v1/img/samI")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(writeJson(basicRequest())))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_JPEG));
    }

    @Test
    void obbArea_returnsResult() throws Exception {
        ObbDetection detection = new ObbDetection(1f, 2f, 3f, 4f, 0.1f, 0.9f, 1, "obj", List.of());
        when(imgAnalysisService.obbArea(any(DetectionRequestWithArea.class)))
                .thenReturn(List.of(detection));

        mockMvc.perform(post("/api/v1/img/obb")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(writeJson(areaRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }

    @Test
    void obbAreaI_returnsImage() throws Exception {
        when(imgAnalysisService.obbAreaI(any(DetectionRequestWithArea.class)))
                .thenReturn(sampleImage());

        mockMvc.perform(post("/api/v1/img/obbI")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(writeJson(areaRequest())))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_JPEG));
    }

    @Test
    void segAreaI_returnsImage() throws Exception {
        when(imgAnalysisService.segAreaI(any(DetectionRequestWithArea.class)))
                .thenReturn(sampleImage());

        mockMvc.perform(post("/api/v1/img/segI")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(writeJson(areaRequest())))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_JPEG));
    }

    @Test
    void segArea_returnsResult() throws Exception {
        SegDetection segDetection = new SegDetection();
        segDetection.setScore(0.9f);
        segDetection.setClassId(1);
        segDetection.setClassName("seg");
        when(imgAnalysisService.segArea(any(DetectionRequestWithArea.class)))
                .thenReturn(List.of(segDetection));

        mockMvc.perform(post("/api/v1/img/seg")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(writeJson(areaRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }

    private DetectionRequestWithArea areaRequest() {
        DetectionRequestWithArea request = new DetectionRequestWithArea();
        request.setImgUrl("http://example.com/test.jpg");
        return request;
    }

    private DetectionRequest basicRequest() {
        DetectionRequest request = new DetectionRequest();
        request.setImgUrl("http://example.com/test.jpg");
        return request;
    }

    private BufferedImage sampleImage() {
        return new BufferedImage(12, 12, BufferedImage.TYPE_INT_RGB);
    }

    private String writeJson(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}
