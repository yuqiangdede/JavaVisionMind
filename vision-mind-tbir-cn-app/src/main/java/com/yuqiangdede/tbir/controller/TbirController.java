package com.yuqiangdede.tbir.controller;


import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import javax.imageio.ImageIO;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.ObjectUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.yuqiangdede.common.util.ImageUtil;
import com.yuqiangdede.common.util.JsonUtils;
import com.yuqiangdede.tbir.dto.input.DeleteImageRequest;
import com.yuqiangdede.tbir.dto.input.ImgSearchUrlRequest;
import com.yuqiangdede.tbir.dto.input.SaveImageRequest;
import com.yuqiangdede.tbir.dto.input.SearchImageRequest;
import com.yuqiangdede.tbir.dto.input.SearchRequest;
import com.yuqiangdede.tbir.dto.input.SimilarityImageImageRequest;
import com.yuqiangdede.tbir.dto.input.SimilarityTextImageRequest;
import com.yuqiangdede.common.dto.output.HttpResult;
import com.yuqiangdede.tbir.dto.output.ImageSaveResult;
import com.yuqiangdede.tbir.dto.output.SearchResult;
import com.yuqiangdede.tbir.dto.output.SimilarityResult;
import com.yuqiangdede.tbir.service.TbirService;

import ai.onnxruntime.OrtException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
@RestController
@Slf4j
@RequestMapping("/api")
@RequiredArgsConstructor
public class TbirController {

    private final TbirService tbirService;

    @PostMapping(value = "/v1/tbir/saveImg", produces = "application/json", consumes = "application/json")
    public HttpResult<?> saveImg(@RequestBody SaveImageRequest input) {
        long start_time = System.currentTimeMillis();
        boolean hasBatch = input != null && input.getImgUrls() != null && !input.getImgUrls().isEmpty();
        boolean hasSingle = input != null && !ObjectUtils.isEmpty(input.getImgUrl());
        if (!hasBatch && !hasSingle) {
            return new HttpResult<>(false, "imgUrl/imgUrls is null or empty");
        }
        try {
            Object result;
            if (hasBatch) {
                result = tbirService.saveImgs(input);
            } else {
                result = tbirService.saveImg(input);
            }

            log.info("SaveImg :Input：{}. Result:{}. Cost time:{} ms.", input, JsonUtils.object2Json(result), (System.currentTimeMillis() - start_time));
            return new HttpResult<>(true, "success", result);
        } catch (IOException | OrtException e) {
            log.error("saveImg error", e);
            return new HttpResult<>(false, e.getMessage());
        } catch (RuntimeException e) {
            log.error("saveImg unexpected error", e);
            return new HttpResult<>(false, "internal error");
        }
    }

    @PostMapping(value = "/v1/tbir/deleteImg", produces = "application/json", consumes = "application/json")
    public HttpResult<Void> deleteImg(@RequestBody DeleteImageRequest input) {
        long start_time = System.currentTimeMillis();
        if (ObjectUtils.isEmpty(input.getImgId())) {
            return new HttpResult<>(false, "id is null or empty");
        }
        try {
            tbirService.deleteImg(input);
            log.info("deleteImg : {}. Cost time:{} ms.", input, (System.currentTimeMillis() - start_time));
            return new HttpResult<>(true, "success");
        } catch (RuntimeException e) {
            log.error("deleteImg error", e);
            return new HttpResult<>(false, "internal error");
        }
    }

    @PostMapping(value = "/v1/tbir/searchImg", produces = "application/json", consumes = "application/json")
    public HttpResult<SearchResult> searchImg(@RequestBody SearchImageRequest input) {
        long start_time = System.currentTimeMillis();
        if (ObjectUtils.isEmpty(input.getImgId())) {
            return new HttpResult<>(false, "id is null or empty");
        }
        try {
            SearchResult result = tbirService.searchImg(input.getImgId());
            log.info("searchImg : {}. Cost time:{} ms.", input, (System.currentTimeMillis() - start_time));
            return new HttpResult<>(true, "success", result);
        } catch (RuntimeException e) {
            log.error("searchImg error", e);
            return new HttpResult<>(false, "internal error");
        }
    }

    @PostMapping(value = "/v1/tbir/searchImgI", produces = "application/json", consumes = "application/json")
    public Object searchImgI(@RequestBody SearchImageRequest input) {
        long start_time = System.currentTimeMillis();
        if (ObjectUtils.isEmpty(input.getImgId())) {
            return new HttpResult<>(false, "id is null or empty");
        }
        try {
            List<BufferedImage> images = tbirService.searchImgI(input.getImgId());

            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            ImageIO.write(images.get(0), "jpg", byteArrayOutputStream);
            // 设置 HTTP 响应头
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.IMAGE_JPEG);

            log.info("searchImgI : {}. Cost time:{} ms.", input, (System.currentTimeMillis() - start_time));
            return new ResponseEntity<>(byteArrayOutputStream.toByteArray(), headers, HttpStatus.OK);
        } catch (IOException e) {
            log.error("searchImgI error", e);
            return new HttpResult<>(false, e.getMessage());
        } catch (RuntimeException e) {
            log.error("searchImgI unexpected error", e);
            return new HttpResult<>(false, "internal error");
        }
    }

    @PostMapping(value = "/v1/tbir/search", produces = "application/json", consumes = "application/json")
    public HttpResult<SearchResult> search(@RequestBody SearchRequest input) {
        long start_time = System.currentTimeMillis();
        if (ObjectUtils.isEmpty(input.getQuery())) {
            return new HttpResult<>(false, "id is null or empty");
        }
        try {
            SearchResult result = tbirService.searchByText(input.getQuery(), input.getCameraId(), input.getGroupId(), input.getTopN());
            log.info("searchByText : Input：{}. Result:{}. Cost time:{} ms.", input, JsonUtils.object2Json(result), (System.currentTimeMillis() - start_time));
            return new HttpResult<>(true, "success", result);
        } catch (IOException e) {
            log.error("searchByText error", e);
            return new HttpResult<>(false, e.getMessage());
        } catch (RuntimeException e) {
            log.error("searchByText unexpected error", e);
            return new HttpResult<>(false, "internal error");
        }
    }

    @PostMapping(value = "/v1/tbir/searchI", consumes = "application/json", produces = {MediaType.IMAGE_JPEG_VALUE, MediaType.APPLICATION_JSON_VALUE})
    public Object searchI(@RequestBody SearchRequest input) {
        long start_time = System.currentTimeMillis();
        if (ObjectUtils.isEmpty(input.getQuery())) {
            return new HttpResult<>(false, "id is null or empty");
        }
        try {
            List<BufferedImage> images = tbirService.searchByTextI(input.getQuery(), input.getCameraId(), input.getGroupId(), input.getTopN());
            if (images == null || images.isEmpty()) {
                log.info("searchByTextI : {}. no result. Cost time:{} ms.", input, (System.currentTimeMillis() - start_time));
                return new HttpResult<>(false, "no matched images");
            }

            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            ImageIO.write(images.get(0), "jpg", byteArrayOutputStream);
            // 设置 HTTP 响应头
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.IMAGE_JPEG);


            log.info("searchByTextI : {}. Cost time:{} ms.", input, (System.currentTimeMillis() - start_time));
            return new ResponseEntity<>(byteArrayOutputStream.toByteArray(), headers, HttpStatus.OK);
        } catch (IOException e) {
            log.error("searchByTextI error", e);
            return new HttpResult<>(false, e.getMessage());
        } catch (RuntimeException e) {
            log.error("searchByTextI unexpected error", e);
            return new HttpResult<>(false, "internal error");
        }
    }

    @PostMapping(value = "/v1/tbir/imgSearch", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public HttpResult<SearchResult> imgSearch(@RequestParam("image") MultipartFile imageFile, @RequestParam("topN") Integer topN) {
        try (InputStream in = imageFile.getInputStream()) {
            BufferedImage bufferedImage = ImageIO.read(in);
            SearchResult result = tbirService.imgSearch(bufferedImage, topN);
            return new HttpResult<>(true, "success", result);
        } catch (IOException | OrtException e) {
            log.error("imgSearch error", e);
            return new HttpResult<>(false, e.getMessage());
        } catch (RuntimeException e) {
            log.error("imgSearch unexpected error", e);
            return new HttpResult<>(false, "internal error");
        }
    }

    @PostMapping(value = "/v1/tbir/imgSearch", consumes = "application/json", produces = "application/json")
    public HttpResult<SearchResult> imgSearchByUrl(@RequestBody ImgSearchUrlRequest input) {
        if (input == null || ObjectUtils.isEmpty(input.getImgUrl())) {
            return new HttpResult<>(false, "imgUrl is null or empty");
        }
        Integer topN = input.getTopN() == null ? 10 : input.getTopN();
        try {
            BufferedImage bufferedImage = ImageUtil.urlToImage(input.getImgUrl());
            SearchResult result = tbirService.imgSearch(bufferedImage, topN);
            return new HttpResult<>(true, "success", result);
        } catch (IOException | OrtException e) {
            log.error("imgSearchByUrl error", e);
            return new HttpResult<>(false, e.getMessage());
        } catch (RuntimeException e) {
            log.error("imgSearchByUrl unexpected error", e);
            return new HttpResult<>(false, "internal error");
        }
    }

    @PostMapping(value = "/v1/tbir/similarity/text-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = "application/json")
    public HttpResult<SimilarityResult> similarityTextImage(@RequestParam("text") String text,
                                                           @RequestParam("image") MultipartFile imageFile) {
        try (InputStream in = imageFile.getInputStream()) {
            BufferedImage image = ImageIO.read(in);
            SimilarityResult result = tbirService.similarityTextImage(text, image);
            return new HttpResult<>(true, "success", result);
        } catch (IOException | OrtException e) {
            log.error("similarityTextImage error", e);
            return new HttpResult<>(false, e.getMessage());
        } catch (RuntimeException e) {
            log.error("similarityTextImage unexpected error", e);
            return new HttpResult<>(false, "internal error");
        }
    }

    @PostMapping(value = "/v1/tbir/similarity/text-image", consumes = "application/json", produces = "application/json")
    public HttpResult<SimilarityResult> similarityTextImageByUrl(@RequestBody SimilarityTextImageRequest input) {
        if (input == null || ObjectUtils.isEmpty(input.getText())) {
            return new HttpResult<>(false, "text is null or empty");
        }
        if (ObjectUtils.isEmpty(input.getImgUrl())) {
            return new HttpResult<>(false, "imgUrl is null or empty");
        }
        try {
            BufferedImage image = ImageUtil.urlToImage(input.getImgUrl());
            SimilarityResult result = tbirService.similarityTextImage(input.getText(), image);
            return new HttpResult<>(true, "success", result);
        } catch (IOException | OrtException e) {
            log.error("similarityTextImageByUrl error", e);
            return new HttpResult<>(false, e.getMessage());
        } catch (RuntimeException e) {
            log.error("similarityTextImageByUrl unexpected error", e);
            return new HttpResult<>(false, "internal error");
        }
    }

    @PostMapping(value = "/v1/tbir/similarity/image-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = "application/json")
    public HttpResult<SimilarityResult> similarityImageImage(@RequestParam("image1") MultipartFile imageFile1,
                                                            @RequestParam("image2") MultipartFile imageFile2) {
        try (InputStream in1 = imageFile1.getInputStream(); InputStream in2 = imageFile2.getInputStream()) {
            BufferedImage image1 = ImageIO.read(in1);
            BufferedImage image2 = ImageIO.read(in2);
            SimilarityResult result = tbirService.similarityImageImage(image1, image2);
            return new HttpResult<>(true, "success", result);
        } catch (IOException | OrtException e) {
            log.error("similarityImageImage error", e);
            return new HttpResult<>(false, e.getMessage());
        } catch (RuntimeException e) {
            log.error("similarityImageImage unexpected error", e);
            return new HttpResult<>(false, "internal error");
        }
    }

    @PostMapping(value = "/v1/tbir/similarity/image-image", consumes = "application/json", produces = "application/json")
    public HttpResult<SimilarityResult> similarityImageImageByUrl(@RequestBody SimilarityImageImageRequest input) {
        if (input == null || ObjectUtils.isEmpty(input.getImgUrl1()) || ObjectUtils.isEmpty(input.getImgUrl2())) {
            return new HttpResult<>(false, "imgUrl1/imgUrl2 is null or empty");
        }
        try {
            BufferedImage image1 = ImageUtil.urlToImage(input.getImgUrl1());
            BufferedImage image2 = ImageUtil.urlToImage(input.getImgUrl2());
            SimilarityResult result = tbirService.similarityImageImage(image1, image2);
            return new HttpResult<>(true, "success", result);
        } catch (IOException | OrtException e) {
            log.error("similarityImageImageByUrl error", e);
            return new HttpResult<>(false, e.getMessage());
        } catch (RuntimeException e) {
            log.error("similarityImageImageByUrl unexpected error", e);
            return new HttpResult<>(false, "internal error");
        }
    }
}
