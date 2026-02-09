package com.yuqiangdede.reid.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuqiangdede.reid.output.Feature;
import com.yuqiangdede.reid.output.Human;
import com.yuqiangdede.reid.service.ReidService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReidController.class)
class ReidControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private ReidService reidService;

    @Test
    void featureSingle_returnsResult() throws Exception {
        when(reidService.featureSingle(anyString()))
                .thenReturn(new Feature("uuid1", new float[]{0.1f}));

        mockMvc.perform(post("/api/v1/reid/feature/single")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(writeJson(Map.of("imgUrl", "http://example.com/a.jpg"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }

    @Test
    void calculateSimilarity_returnsResult() throws Exception {
        when(reidService.calculateSimilarity(anyString(), anyString()))
                .thenReturn(0.5f);

        mockMvc.perform(post("/api/v1/reid/feature/calculateSimilarity")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(writeJson(Map.of("imgUrl1", "http://example.com/a.jpg", "imgUrl2", "http://example.com/b.jpg"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }

    @Test
    void featureMulti_returnsResult() throws Exception {
        when(reidService.featureMulti(anyString()))
                .thenReturn(List.of(new Feature("uuid1", new float[]{0.1f})));

        mockMvc.perform(post("/api/v1/reid/feature/multi")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(writeJson(Map.of("imgUrl", "http://example.com/a.jpg"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }

    @Test
    void storeSingle_returnsResult() throws Exception {
        when(reidService.storeSingle(anyString(), anyString(), anyString()))
                .thenReturn(new Feature("uuid1", new float[]{0.2f}));

        mockMvc.perform(post("/api/v1/reid/store/single")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(writeJson(Map.of(
                                "imgUrl", "http://example.com/a.jpg",
                                "cameraId", "cam1",
                                "humanId", "human1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }

    @Test
    void search_returnsResult() throws Exception {
        when(reidService.search(anyString(), anyString(), anyInt(), anyFloat()))
                .thenReturn(List.of(new Human("h1", "i1", "http://example.com/a.jpg", 0.7f, "cam1", "type")));

        mockMvc.perform(post("/api/v1/reid/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(writeJson(Map.of(
                                "imgUrl", "http://example.com/a.jpg",
                                "cameraId", "cam1",
                                "topN", "5",
                                "threshold", "0.5"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }

    @Test
    void searchOrStore_returnsResult() throws Exception {
        when(reidService.searchOrStore(anyString(), anyFloat()))
                .thenReturn(new Human("h1", "i1", "http://example.com/a.jpg", 0.7f, "cam1", "type"));

        mockMvc.perform(post("/api/v1/reid/searchOrStore")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(writeJson(Map.of(
                                "imgUrl", "http://example.com/a.jpg",
                                "threshold", "0.6"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }

    @Test
    void associateStore_returnsResult() throws Exception {
        when(reidService.associateStore(anyString(), anyFloat()))
                .thenReturn(new Human("h1", "i1", "http://example.com/a.jpg", 0.7f, "cam1", "type"));

        mockMvc.perform(post("/api/v1/reid/associateStore")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(writeJson(Map.of(
                                "imgUrl", "http://example.com/a.jpg",
                                "threshold", "0.6"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }

    private String writeJson(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}
