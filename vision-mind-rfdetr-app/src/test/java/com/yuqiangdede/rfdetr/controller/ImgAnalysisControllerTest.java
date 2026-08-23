package com.yuqiangdede.rfdetr.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuqiangdede.common.dto.output.Box;
import com.yuqiangdede.rfdetr.dto.input.DetectionRequestWithArea;
import com.yuqiangdede.rfdetr.service.RfDetrImageAnalysisService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.awt.image.BufferedImage;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
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
    private RfDetrImageAnalysisService imageAnalysisService;

    @Test
    void detect_supportsUnifiedAndCompatibilityRoutes() throws Exception {
        when(imageAnalysisService.detectArea(any(DetectionRequestWithArea.class))).thenReturn(List.of(new Box(1, 2, 3, 4)));

        for (String route : List.of("/api/v1/vision/detect", "/api/v1/img/detect")) {
            mockMvc.perform(post(route).contentType(MediaType.APPLICATION_JSON).content(json(validRequest())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value("0"))
                    .andExpect(jsonPath("$.data[0].x1").value(1.0));
        }
    }

    @Test
    void detectPreview_returnsJpeg() throws Exception {
        when(imageAnalysisService.detectAreaPreview(any(DetectionRequestWithArea.class)))
                .thenReturn(new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB));

        mockMvc.perform(post("/api/v1/vision/detect/preview")
                        .contentType(MediaType.APPLICATION_JSON).content(json(validRequest())))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_JPEG));
    }

    @Test
    void detectUpload_returnsBoxes() throws Exception {
        when(imageAnalysisService.detectUpload(any(BufferedImage.class), eq(0.3f), eq("1,3")))
                .thenReturn(List.of(new Box(1, 2, 3, 4)));
        MockMultipartFile file = new MockMultipartFile("file", "image.png", "image/png", png());

        mockMvc.perform(multipart("/api/v1/vision/detect/upload").file(file)
                        .param("threshold", "0.3").param("types", "1,3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }

    @Test
    void detect_missingImage_returnsCompatibilityError() throws Exception {
        mockMvc.perform(post("/api/v1/vision/detect").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("-1"))
                .andExpect(jsonPath("$.msg").value("imgurl is null or empty"));
    }

    private DetectionRequestWithArea validRequest() {
        DetectionRequestWithArea request = new DetectionRequestWithArea();
        request.setImgUrl("file:///test.jpg");
        return request;
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private byte[] png() throws Exception {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(image, "png", output);
        return output.toByteArray();
    }
}
