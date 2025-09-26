package com.yuqiangdede.tbir.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuqiangdede.common.dto.Point;
import com.yuqiangdede.common.dto.output.Box;
import com.yuqiangdede.tbir.dto.input.DeleteImageRequest;
import com.yuqiangdede.tbir.dto.input.SaveImageRequest;
import com.yuqiangdede.tbir.dto.input.SearchRequest;
import com.yuqiangdede.tbir.dto.output.HitImage;
import com.yuqiangdede.tbir.dto.output.ImageSaveResult;
import com.yuqiangdede.tbir.dto.output.SearchResult;
import com.yuqiangdede.tbir.service.TbirService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TbirController.class)
class TbirControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private TbirService tbirService;

    @Test
    void saveImg_returnsImageId() throws Exception {
        SaveImageRequest request = buildSaveImageRequest();
        when(tbirService.saveImg(any(SaveImageRequest.class))).thenReturn(new ImageSaveResult("img-1"));

        mockMvc.perform(post("/api/v1/tbir/saveImg")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJson(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.imageId").value("img-1"));

        verify(tbirService).saveImg(any(SaveImageRequest.class));
    }

    @Test
    void deleteImg_returnsSuccess() throws Exception {
        Map<String, String> payload = Map.of("imageId", "img-1");

        mockMvc.perform(post("/api/v1/tbir/deleteImg")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJson(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));

        verify(tbirService).deleteImg(any(DeleteImageRequest.class));
    }

    @Test
    void searchImg_returnsResults() throws Exception {
        Map<String, String> payload = Map.of("imageId", "img-1");
        when(tbirService.searchImg("img-1")).thenReturn(sampleSearchResult());

        mockMvc.perform(post("/api/v1/tbir/searchImg")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJson(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.results[0].imageId").value("img-hit"));

        verify(tbirService).searchImg("img-1");
    }

    @Test
    void searchImgI_returnsImage() throws Exception {
        Map<String, String> payload = Map.of("imageId", "img-1");
        when(tbirService.searchImgI("img-1")).thenReturn(List.of(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB)));

        mockMvc.perform(post("/api/v1/tbir/searchImgI")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJson(payload)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_JPEG))
                .andExpect(result -> assertThat(result.getResponse().getContentAsByteArray()).isNotEmpty());

        verify(tbirService).searchImgI("img-1");
    }

    @Test
    void searchByText_returnsResults() throws Exception {
        SearchRequest request = buildSearchRequest();
        when(tbirService.searchByText(anyString(), anyString(), anyString(), anyInt())).thenReturn(sampleSearchResult());

        mockMvc.perform(post("/api/v1/tbir/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJson(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.totalHits").value(1));

        verify(tbirService).searchByText("person walking", "cam-1", "group-1", 5);
    }

    @Test
    void searchByTextI_returnsImage() throws Exception {
        SearchRequest request = buildSearchRequest();
        when(tbirService.searchByTextI(anyString(), anyString(), anyString(), anyInt())).thenReturn(List.of(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB)));

        mockMvc.perform(post("/api/v1/tbir/searchI")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJson(request)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_JPEG))
                .andExpect(result -> assertThat(result.getResponse().getContentAsByteArray()).isNotEmpty());

        verify(tbirService).searchByTextI("person walking", "cam-1", "group-1", 5);
    }

    @Test
    void imgSearch_returnsResults() throws Exception {
        when(tbirService.imgSearch(any(BufferedImage.class), anyInt())).thenReturn(sampleSearchResult());
        MockMultipartFile file = new MockMultipartFile("image", "sample.jpg", MediaType.IMAGE_JPEG_VALUE, sampleImageBytes());

        mockMvc.perform(multipart("/api/v1/tbir/imgSearch")
                        .file(file)
                        .param("topN", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.totalHits").value(1));

        verify(tbirService).imgSearch(any(BufferedImage.class), Mockito.eq(3));
    }

    private SaveImageRequest buildSaveImageRequest() {
        SaveImageRequest request = new SaveImageRequest();
        request.setImgUrl("https://example.com/a.jpg");
        request.setThreshold(0.3f);
        request.setTypes("1,2");
        request.setImgId("img-1");
        request.setCameraId("cam-1");
        request.setGroupId("group-1");
        request.setMeta(Collections.singletonMap("scene", "outdoor"));
        request.setDetectionFrames(buildFrame());
        request.setBlockingFrames(new ArrayList<>());
        return request;
    }

    private ArrayList<ArrayList<Point>> buildFrame() {
        ArrayList<Point> frame = new ArrayList<>();
        frame.add(new Point(0f, 0f));
        frame.add(new Point(10f, 0f));
        frame.add(new Point(10f, 10f));
        frame.add(new Point(0f, 10f));
        ArrayList<ArrayList<Point>> frames = new ArrayList<>();
        frames.add(frame);
        return frames;
    }

    private SearchRequest buildSearchRequest() {
        SearchRequest request = new SearchRequest();
        request.setQuery("person walking");
        request.setCameraId("cam-1");
        request.setGroupId("group-1");
        request.setTopN(5);
        return request;
    }

    private SearchResult sampleSearchResult() {
        HitImage hit = new HitImage();
        hit.setImageId("img-hit");
        hit.setScore(0.9);
        hit.setImageUrl("https://example.com/result.jpg");
        hit.setMatchedBoxes(List.of(new Box(1f, 2f, 3f, 4f)));
        return new SearchResult(List.of(hit));
    }

    private byte[] sampleImageBytes() throws Exception {
        BufferedImage img = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(img, "jpg", out);
            return out.toByteArray();
        }
    }

    private String asJson(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}
