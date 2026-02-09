package com.yuqiangdede.tbir.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuqiangdede.tbir.dto.input.SaveImageRequest;
import com.yuqiangdede.tbir.dto.input.SearchRequest;
import com.yuqiangdede.tbir.dto.output.ImageSaveResult;
import com.yuqiangdede.tbir.dto.output.SearchResult;
import com.yuqiangdede.tbir.dto.output.SimilarityResult;
import com.yuqiangdede.tbir.service.TbirService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.nullable;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TbirController.class)
class TbirControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private TbirService tbirService;

    @Test
    void saveImg_returnsResult() throws Exception {
        SaveImageRequest request = new SaveImageRequest();
        request.setImgUrl("http://example.com/img.jpg");
        when(tbirService.saveImg(any(SaveImageRequest.class)))
                .thenReturn(new ImageSaveResult("img1"));

        mockMvc.perform(post("/api/v1/tbir/saveImg")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(writeJson(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }

    @Test
    void deleteImg_returnsResult() throws Exception {
        mockMvc.perform(post("/api/v1/tbir/deleteImg")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(writeJson(Map.of("imageId", "img1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }

    @Test
    void searchImg_returnsResult() throws Exception {
        when(tbirService.searchImg(anyString()))
                .thenReturn(new SearchResult(List.of()));

        mockMvc.perform(post("/api/v1/tbir/searchImg")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(writeJson(Map.of("imageId", "img1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }

    @Test
    void searchImgI_returnsImage() throws Exception {
        when(tbirService.searchImgI(anyString()))
                .thenReturn(List.of(sampleImage()));

        mockMvc.perform(post("/api/v1/tbir/searchImgI")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(writeJson(Map.of("imageId", "img1"))))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_JPEG));
    }

    @Test
    void search_returnsResult() throws Exception {
        when(tbirService.searchByText(anyString(), nullable(String.class), nullable(String.class), anyInt()))
                .thenReturn(new SearchResult(List.of()));

        SearchRequest request = new SearchRequest();
        request.setQuery("car");
        request.setTopN(5);

        mockMvc.perform(post("/api/v1/tbir/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(writeJson(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }

    @Test
    void searchI_returnsImage() throws Exception {
        when(tbirService.searchByTextI(anyString(), nullable(String.class), nullable(String.class), anyInt()))
                .thenReturn(List.of(sampleImage()));

        SearchRequest request = new SearchRequest();
        request.setQuery("car");
        request.setTopN(5);

        mockMvc.perform(post("/api/v1/tbir/searchI")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(writeJson(request)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_JPEG));
    }

    @Test
    void imgSearch_returnsResult() throws Exception {
        when(tbirService.imgSearch(any(BufferedImage.class), anyInt()))
                .thenReturn(new SearchResult(List.of()));

        MockMultipartFile file = new MockMultipartFile(
                "image",
                "img.jpg",
                MediaType.IMAGE_JPEG_VALUE,
                imageBytes());

        mockMvc.perform(multipart("/api/v1/tbir/imgSearch")
                        .file(file)
                        .param("topN", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }

    @Test
    void imgSearchByUrl_returnsValidationError() throws Exception {
        mockMvc.perform(post("/api/v1/tbir/imgSearch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(writeJson(Map.of("imgUrl", ""))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("-1"));
    }

    @Test
    void similarityTextImage_returnsResult() throws Exception {
        when(tbirService.similarityTextImage(anyString(), any(BufferedImage.class)))
                .thenReturn(new SimilarityResult(0.6d));

        MockMultipartFile file = new MockMultipartFile(
                "image",
                "img.jpg",
                MediaType.IMAGE_JPEG_VALUE,
                imageBytes());

        mockMvc.perform(multipart("/api/v1/tbir/similarity/text-image")
                        .file(file)
                        .param("text", "car"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }

    @Test
    void similarityTextImageByUrl_returnsValidationError() throws Exception {
        mockMvc.perform(post("/api/v1/tbir/similarity/text-image")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(writeJson(Map.of("text", "", "imgUrl", ""))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("-1"));
    }

    @Test
    void similarityImageImage_returnsResult() throws Exception {
        when(tbirService.similarityImageImage(any(BufferedImage.class), any(BufferedImage.class)))
                .thenReturn(new SimilarityResult(0.7d));

        MockMultipartFile image1 = new MockMultipartFile(
                "image1",
                "img1.jpg",
                MediaType.IMAGE_JPEG_VALUE,
                imageBytes());
        MockMultipartFile image2 = new MockMultipartFile(
                "image2",
                "img2.jpg",
                MediaType.IMAGE_JPEG_VALUE,
                imageBytes());

        mockMvc.perform(multipart("/api/v1/tbir/similarity/image-image")
                        .file(image1)
                        .file(image2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }

    @Test
    void similarityImageImageByUrl_returnsValidationError() throws Exception {
        mockMvc.perform(post("/api/v1/tbir/similarity/image-image")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(writeJson(Map.of("imgUrl1", "", "imgUrl2", ""))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("-1"));
    }

    private BufferedImage sampleImage() {
        return new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
    }

    private byte[] imageBytes() throws Exception {
        BufferedImage image = sampleImage();
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", outputStream);
        return outputStream.toByteArray();
    }

    private String writeJson(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}
