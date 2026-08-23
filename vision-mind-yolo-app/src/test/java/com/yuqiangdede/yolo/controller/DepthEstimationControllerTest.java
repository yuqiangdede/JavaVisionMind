package com.yuqiangdede.yolo.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuqiangdede.yolo.config.DepthRequestBodyLimitConfig;
import com.yuqiangdede.yolo.dto.input.DepthEstimationRequest;
import com.yuqiangdede.yolo.dto.output.DepthEstimationResult;
import com.yuqiangdede.yolo.service.DepthEstimationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.awt.image.BufferedImage;
import java.io.IOException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DepthEstimationController.class)
@Import(DepthRequestBodyLimitConfig.class)
@TestPropertySource(properties = "vision-mind.yolo.depth.max-request-bytes=256")
class DepthEstimationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private DepthEstimationService depthEstimationService;

    @Test
    void estimate_returnsSummaryWithoutDepthArrayByDefault() throws Exception {
        when(depthEstimationService.estimate(any(DepthEstimationRequest.class))).thenReturn(sampleResult());

        mockMvc.perform(post("/api/v1/vision/depth")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request(false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.width").value(2))
                .andExpect(jsonPath("$.data.unit").value("m"))
                .andExpect(jsonPath("$.data.depthMap").doesNotExist());
    }

    @Test
    void estimate_canIncludeDepthArrayExplicitly() throws Exception {
        when(depthEstimationService.estimate(any(DepthEstimationRequest.class))).thenReturn(sampleResult());

        mockMvc.perform(post("/api/v1/vision/depth")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request(true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.depthMap.length()").value(4))
                .andExpect(jsonPath("$.data.depthMap[0]").value(1.0));
    }

    @Test
    void depthMap_returnsLittleEndianFloat32WithShapeHeaders() throws Exception {
        when(depthEstimationService.estimate(any(DepthEstimationRequest.class))).thenReturn(sampleResult());

        mockMvc.perform(post("/api/v1/vision/depth/map")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request(false))))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_OCTET_STREAM))
                .andExpect(header().string("X-Depth-Width", "2"))
                .andExpect(header().string("X-Depth-Height", "2"))
                .andExpect(header().string("X-Depth-Dtype", "float32-le"))
                .andExpect(content().bytes(new byte[]{0, 0, -128, 63, 0, 0, 0, 64,
                        0, 0, 64, 64, 0, 0, -128, 64}));
    }

    @Test
    void preview_returnsPng() throws Exception {
        when(depthEstimationService.preview(any(DepthEstimationRequest.class)))
                .thenReturn(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB));

        mockMvc.perform(post("/api/v1/vision/depth/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request(false))))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG));
    }

    @Test
    void estimate_rejectsMissingImageUrl() throws Exception {
        mockMvc.perform(post("/api/v1/vision/depth")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("-1"))
                .andExpect(jsonPath("$.msg").value("imgurl is null or empty"));
    }

    @Test
    void depthMap_returnsJsonFailureForBlankImageUrl() throws Exception {
        mockMvc.perform(post("/api/v1/vision/depth/map")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"imgUrl\":\"   \"}"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("-1"));
    }

    @Test
    void estimate_returnsFixedErrorWithoutLeakingCause() throws Exception {
        when(depthEstimationService.estimate(any(DepthEstimationRequest.class)))
                .thenThrow(new IOException("SECRET signed-url token"));

        mockMvc.perform(post("/api/v1/vision/depth")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request(false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("-1"))
                .andExpect(jsonPath("$.msg").value("depth estimation failed"))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("SECRET"))));
    }

    @Test
    void depthMap_returnsFixedJsonErrorWhenServiceFails() throws Exception {
        when(depthEstimationService.estimate(any(DepthEstimationRequest.class)))
                .thenThrow(new IOException("SECRET signed-url token"));

        mockMvc.perform(post("/api/v1/vision/depth/map")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request(false))))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("-1"))
                .andExpect(jsonPath("$.msg").value("depth estimation failed"))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("SECRET"))));
    }

    @Test
    void estimate_returnsFixedFailureWhenJsonDepthMapExceedsLimit() throws Exception {
        when(depthEstimationService.estimate(any(DepthEstimationRequest.class))).thenReturn(sampleResult());
        doThrow(new IllegalArgumentException("depth map is too large for JSON; use /depth/map"))
                .when(depthEstimationService).validateJsonDepthMap(any(DepthEstimationResult.class));

        mockMvc.perform(post("/api/v1/vision/depth")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request(true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("-1"))
                .andExpect(jsonPath("$.msg").value("depth estimation failed"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void depthRequestBodyLimitRejectsBeforeController() throws Exception {
        mockMvc.perform(post("/api/v1/vision/depth")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("x".repeat(257)))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("-1"))
                .andExpect(jsonPath("$.msg").value("request body too large"));

        verifyNoInteractions(depthEstimationService);
    }

    private DepthEstimationRequest request(boolean includeDepthMap) {
        DepthEstimationRequest request = new DepthEstimationRequest();
        request.setImgUrl("file:///test.jpg");
        request.setIncludeDepthMap(includeDepthMap);
        return request;
    }

    private DepthEstimationResult sampleResult() {
        return new DepthEstimationResult(2, 2, "m", "row-major", 4,
                1f, 4f, 2.5f, 2.5f, new float[]{1f, 2f, 3f, 4f});
    }
}
