package com.yuqiangdede.ffe.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuqiangdede.ffe.core.domain.FaceImage;
import com.yuqiangdede.ffe.dto.input.Input4Compare;
import com.yuqiangdede.ffe.dto.input.Input4Del;
import com.yuqiangdede.ffe.dto.input.Input4Save;
import com.yuqiangdede.ffe.dto.input.Input4Search;
import com.yuqiangdede.ffe.dto.input.InputWithUrl;
import com.yuqiangdede.ffe.dto.output.FaceInfo4Search;
import com.yuqiangdede.ffe.dto.output.FaceInfo4SearchAdd;
import com.yuqiangdede.ffe.service.FaceService;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FaceController.class)
class FaceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private FaceService faceService;

    @Test
    void computeFaceVector_returnsResult() throws Exception {
        when(faceService.computeFaceVector(any(InputWithUrl.class)))
                .thenReturn(FaceImage.build("img", List.of()));

        mockMvc.perform(post("/api/v1/face/computeFaceVector")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(writeJson(new InputWithUrl("http://example.com/face.jpg", null, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }

    @Test
    void saveFaceVector_returnsSuccess() throws Exception {
        Input4Save input = new Input4Save("http://example.com/face.jpg", null, "id1", new float[]{0.1f});

        mockMvc.perform(post("/api/v1/face/saveFaceVector")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(writeJson(input)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }

    @Test
    void computeAndSaveFaceVector_returnsList() throws Exception {
        when(faceService.computeAndSaveFaceVector(any(InputWithUrl.class)))
                .thenReturn(FaceImage.build("img", List.of()));

        mockMvc.perform(post("/api/v1/face/computeAndSaveFaceVector")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(writeJson(new InputWithUrl("http://example.com/face.jpg", null, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }

    @Test
    void deleteFace_returnsSuccess() throws Exception {
        mockMvc.perform(post("/api/v1/face/deleteFace")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(writeJson(new Input4Del("id1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }

    @Test
    void findMostSimilarFace_returnsResult() throws Exception {
        when(faceService.findMostSimilarFace(any(Input4Search.class)))
                .thenReturn(List.of(new FaceInfo4Search("id1", "http://example.com/img.jpg", 0.9f)));

        mockMvc.perform(post("/api/v1/face/findMostSimilarFace")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(writeJson(new Input4Search("http://example.com/face.jpg", null, null, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }

    @Test
    void findMostSimilarFaceI_returnsValidationError() throws Exception {
        Input4Search input = new Input4Search("", null, null, null);

        mockMvc.perform(post("/api/v1/face/findMostSimilarFaceI")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(writeJson(input)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("-1"));
    }

    @Test
    void calculateSimilarity_returnsResult() throws Exception {
        when(faceService.calculateSimilarity(any(Input4Compare.class)))
                .thenReturn(0.8d);

        mockMvc.perform(post("/api/v1/face/calculateSimilarity")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(writeJson(new Input4Compare("http://example.com/a.jpg", "http://example.com/b.jpg"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }

    @Test
    void findSave_returnsResult() throws Exception {
        when(faceService.findSave(any(Input4Search.class)))
                .thenReturn(new FaceInfo4SearchAdd(List.of(), List.of()));

        mockMvc.perform(post("/api/v1/face/findSave")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(writeJson(new Input4Search("http://example.com/face.jpg", null, null, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }

    private String writeJson(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}
