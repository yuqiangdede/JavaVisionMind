package com.yuqiangdede.reid.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuqiangdede.reid.output.Feature;
import com.yuqiangdede.reid.output.Human;
import com.yuqiangdede.reid.service.ReidService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReidController.class)
class ReidControllerTest {

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
    private ReidService reidService;

    @Test
    void featureSingle_returnsFeature() throws Exception {
        Map<String, String> payload = Map.of("imgUrl", "https://example.com/a.jpg");
        Feature feature = new Feature("feature-1", new float[]{0.1f, 0.2f});
        when(reidService.featureSingle("https://example.com/a.jpg")).thenReturn(feature);

        mockMvc.perform(post("/api/v1/reid/feature/single")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJson(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.uuid").value("feature-1"));

        verify(reidService).featureSingle("https://example.com/a.jpg");
    }

    @Test
    void storeSingle_returnsFeature() throws Exception {
        Map<String, String> payload = Map.of(
                "imgUrl", "https://example.com/a.jpg",
                "cameraId", "cam-1",
                "humanId", "human-1"
        );
        Feature feature = new Feature("feature-2", new float[]{0.3f, 0.4f});
        when(reidService.storeSingle("https://example.com/a.jpg", "cam-1", "human-1")).thenReturn(feature);

        mockMvc.perform(post("/api/v1/reid/store/single")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJson(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.uuid").value("feature-2"));

        verify(reidService).storeSingle("https://example.com/a.jpg", "cam-1", "human-1");
    }

    @Test
    void calculateSimilarity_returnsScore() throws Exception {
        Map<String, String> payload = Map.of(
                "imgUrl1", "https://example.com/a.jpg",
                "imgUrl2", "https://example.com/b.jpg"
        );
        when(reidService.calculateSimilarity("https://example.com/a.jpg", "https://example.com/b.jpg")).thenReturn(0.85f);

        mockMvc.perform(post("/api/v1/reid/feature/calculateSimilarity")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJson(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data").value(0.85));

        verify(reidService).calculateSimilarity("https://example.com/a.jpg", "https://example.com/b.jpg");
    }

    @Test
    void featureMulti_returnsList() throws Exception {
        Map<String, String> payload = Map.of("imgUrl", "https://example.com/a.jpg");
        List<Feature> features = List.of(new Feature("feature-3", new float[]{0.5f, 0.6f}));
        when(reidService.featureMulti("https://example.com/a.jpg")).thenReturn(features);

        mockMvc.perform(post("/api/v1/reid/feature/multi")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJson(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data[0].uuid").value("feature-3"));

        verify(reidService).featureMulti("https://example.com/a.jpg");
    }

    @Test
    void searchOrStore_returnsHuman() throws Exception {
        Map<String, String> payload = Map.of(
                "imgUrl", "https://example.com/a.jpg",
                "threshold", "0.9"
        );
        Human human = new Human("human-1", "img-1", "https://example.com/result.jpg", 0.91f, "cam-1", "store");
        when(reidService.searchOrStore("https://example.com/a.jpg", 0.9f)).thenReturn(human);

        mockMvc.perform(post("/api/v1/reid/searchOrStore")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJson(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.humanId").value("human-1"));

        verify(reidService).searchOrStore("https://example.com/a.jpg", 0.9f);
    }

    @Test
    void associateStore_returnsHuman() throws Exception {
        Map<String, String> payload = Map.of(
                "imgUrl", "https://example.com/a.jpg",
                "threshold", "0.85"
        );
        Human human = new Human("human-2", "img-2", "https://example.com/result2.jpg", 0.86f, "cam-1", "associate");
        when(reidService.associateStore("https://example.com/a.jpg", 0.85f)).thenReturn(human);

        mockMvc.perform(post("/api/v1/reid/associateStore")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJson(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.humanId").value("human-2"));

        verify(reidService).associateStore("https://example.com/a.jpg", 0.85f);
    }

    private String asJson(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}
