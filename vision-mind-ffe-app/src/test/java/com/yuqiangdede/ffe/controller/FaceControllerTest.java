package com.yuqiangdede.ffe.controller;

import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuqiangdede.ffe.core.domain.FaceImage;
import com.yuqiangdede.ffe.dto.input.Input4Compare;
import com.yuqiangdede.ffe.dto.input.Input4Del;
import com.yuqiangdede.ffe.dto.input.Input4Save;
import com.yuqiangdede.ffe.dto.input.Input4Search;
import com.yuqiangdede.ffe.dto.input.InputWithUrl;
import com.yuqiangdede.ffe.dto.output.FaceInfo4Add;
import com.yuqiangdede.ffe.dto.output.FaceInfo4Search;
import com.yuqiangdede.ffe.dto.output.FaceInfo4SearchAdd;
import com.yuqiangdede.ffe.service.FaceService;

@WebMvcTest(FaceController.class)
class FaceControllerTest {

    static {
        System.setProperty("vision-mind.skip-opencv", "true");
    }


    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private FaceService faceService;

    @Test
    void computeFaceVector_returnsSuccess() throws Exception {
        InputWithUrl request = new InputWithUrl("https://example.com/a.jpg", "default", 0.6f);
        FaceImage faceImage = FaceImage.build("base64", Collections.emptyList());
        when(faceService.computeFaceVector(any(InputWithUrl.class))).thenReturn(faceImage);

        mockMvc.perform(post("/api/v1/face/computeFaceVector")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJson(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));

        verify(faceService).computeFaceVector(any(InputWithUrl.class));
    }

    @Test
    void saveFaceVector_returnsSuccess() throws Exception {
        Input4Save request = new Input4Save("https://example.com/a.jpg", "default", "id-1", new float[]{0.1f, 0.2f});

        mockMvc.perform(post("/api/v1/face/saveFaceVector")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJson(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));

        verify(faceService).saveFaceVector(any(Input4Save.class));
    }

    @Test
    void computeAndSaveFaceVector_returnsList() throws Exception {
        InputWithUrl request = new InputWithUrl("https://example.com/a.jpg", "default", 0.5f);
        FaceImage faceImage = FaceImage.build("base64", Collections.emptyList());
        when(faceService.computeAndSaveFaceVector(any(InputWithUrl.class))).thenReturn(faceImage);

        mockMvc.perform(post("/api/v1/face/computeAndSaveFaceVector")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJson(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data").isArray());

        verify(faceService).computeAndSaveFaceVector(any(InputWithUrl.class));
    }

    @Test
    void deleteFace_returnsSuccess() throws Exception {
        Input4Del request = new Input4Del("face-1");

        mockMvc.perform(post("/api/v1/face/deleteFace")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJson(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));

        verify(faceService).delete(any(Input4Del.class));
    }

    @Test
    void findMostSimilarFace_returnsMatches() throws Exception {
        Input4Search request = new Input4Search("https://example.com/a.jpg", "default", 0.5f, 0.4f);
        List<FaceInfo4Search> result = List.of(new FaceInfo4Search("face-1", "https://example.com/result.jpg", 0.87f));
        when(faceService.findMostSimilarFace(any(Input4Search.class))).thenReturn(result);

        mockMvc.perform(post("/api/v1/face/findMostSimilarFace")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJson(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data[0].id").value("face-1"));

        verify(faceService).findMostSimilarFace(any(Input4Search.class));
    }

    @Test
    void findMostSimilarFaceI_returnsImage() throws Exception {
        Input4Search request = new Input4Search("https://example.com/a.jpg", "default", 0.5f, 0.4f);
        List<FaceInfo4Search> result = List.of(new FaceInfo4Search("face-1", "https://example.com/result.jpg", 0.87f));
        when(faceService.findMostSimilarFace(any(Input4Search.class))).thenReturn(result);

        try (MockedStatic<com.yuqiangdede.common.util.ImageUtil> mocked = Mockito.mockStatic(com.yuqiangdede.common.util.ImageUtil.class)) {
            mocked.when(() -> com.yuqiangdede.common.util.ImageUtil.urlToImage(anyString())).thenReturn(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB));

            mockMvc.perform(post("/api/v1/face/findMostSimilarFaceI")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(asJson(request)))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.IMAGE_JPEG))
                    .andExpect(resultActions -> assertThat(resultActions.getResponse().getContentAsByteArray()).isNotEmpty());
        }

        verify(faceService).findMostSimilarFace(any(Input4Search.class));
    }

    @Test
    void calculateSimilarity_returnsScore() throws Exception {
        Input4Compare request = new Input4Compare("https://example.com/a.jpg", "https://example.com/b.jpg");
        when(faceService.calculateSimilarity(any(Input4Compare.class))).thenReturn(0.91d);

        mockMvc.perform(post("/api/v1/face/calculateSimilarity")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJson(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data").value(0.91));

        verify(faceService).calculateSimilarity(any(Input4Compare.class));
    }

    @Test
    void findSave_returnsCombinedResult() throws Exception {
        Input4Search request = new Input4Search("https://example.com/a.jpg", "default", 0.5f, 0.6f);
        FaceInfo4Add add = new FaceInfo4Add(createFaceInfoStub());
        FaceInfo4Search search = new FaceInfo4Search("face-1", "https://example.com/result.jpg", 0.85f);
        FaceInfo4SearchAdd response = new FaceInfo4SearchAdd(List.of(add), List.of(search));
        when(faceService.findSave(any(Input4Search.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/face/findSave")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJson(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.addList[0].id").value(add.getId()));

        verify(faceService).findSave(any(Input4Search.class));
    }

    private com.yuqiangdede.ffe.core.domain.FaceInfo createFaceInfoStub() {
        com.yuqiangdede.ffe.core.domain.FaceInfo.FaceBox faceBox = com.yuqiangdede.ffe.core.domain.FaceInfo.FaceBox.build(0, 0, 10, 10);
        com.yuqiangdede.ffe.core.domain.FaceInfo.Points points = com.yuqiangdede.ffe.core.domain.FaceInfo.Points.build();
        com.yuqiangdede.ffe.core.domain.FaceInfo faceInfo = com.yuqiangdede.ffe.core.domain.FaceInfo.build(
                0.95f,
                0.1f,
                faceBox,
                points,
                com.yuqiangdede.ffe.core.domain.FaceInfo.Embedding.build("img", new float[]{0.1f, 0.2f})
        );
        faceInfo.setId("face-1");
        faceInfo.setAttribute(com.yuqiangdede.ffe.core.domain.FaceInfo.Attribute.build(
                com.yuqiangdede.ffe.core.domain.FaceInfo.Gender.MALE,
                30
        ));
        return faceInfo;
    }

    private String asJson(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}
