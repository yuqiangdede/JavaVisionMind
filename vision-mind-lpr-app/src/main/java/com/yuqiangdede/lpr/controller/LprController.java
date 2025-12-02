package com.yuqiangdede.lpr.controller;

import com.yuqiangdede.common.dto.output.HttpResult;
import com.yuqiangdede.lpr.controller.dto.PlateRecognitionResult;
import com.yuqiangdede.lpr.service.LprService;
import com.yuqiangdede.yolo.dto.input.DetectionRequestWithArea;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.ObjectUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import ai.onnxruntime.OrtException;
import java.util.List;
import javax.imageio.ImageIO;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
public class LprController {

    private final LprService lprService;

    @PostMapping(value = "/v1/lpr", consumes = "application/json", produces = "application/json")
    public HttpResult<List<PlateRecognitionResult>> recognize(@RequestBody DetectionRequestWithArea request) {
        long start = System.currentTimeMillis();
        if (ObjectUtils.isEmpty(request.getImgUrl())) {
            return new HttpResult<>(false, "imgurl is null or empty");
        }
        try {
            LprService.AnalysisResult analysis = lprService.analyze(request);
            List<PlateRecognitionResult> results = analysis.results();
            log.info("LPR: 输入 {} -> 识别 {} 个车牌, 耗时 {} ms",
                    request.getImgUrl(), results.size(), System.currentTimeMillis() - start);
            return new HttpResult<>(true, results);
        } catch (IOException | OrtException | RuntimeException e) {
            log.error("车牌识别失败", e);
            return new HttpResult<>(false, e.getMessage());
        }
    }

    @PostMapping(value = "/v1/lprI", consumes = "application/json", produces = "image/jpeg")
    public Object recognizeWithImage(@RequestBody DetectionRequestWithArea request) {
        long start = System.currentTimeMillis();
        try {
            LprService.AnalysisResult analysis = lprService.analyze(request);
            BufferedImage overlay = lprService.overlay(analysis);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(overlay, "jpg", baos);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.IMAGE_JPEG);
            log.info("LPR-I: 输入 {} -> 识别 {} 个车牌, 耗时 {} ms",
                    request.getImgUrl(), analysis.results().size(), System.currentTimeMillis() - start);
            return new ResponseEntity<>(baos.toByteArray(), headers, HttpStatus.OK);
        } catch (IOException | OrtException | RuntimeException e) {
            log.error("车牌图像返回失败", e);
            return new HttpResult<>(false, e.getMessage());
        }
    }

    @PostMapping(value = "/v1/lprOcr", consumes = "application/json", produces = "application/json")
    public HttpResult<List<PlateRecognitionResult>> recognizeWithOcr(@RequestBody DetectionRequestWithArea request) {
        long start = System.currentTimeMillis();
        if (ObjectUtils.isEmpty(request.getImgUrl())) {
            return new HttpResult<>(false, "imgurl is null or empty");
        }
        try {
            LprService.AnalysisResult analysis = lprService.analyzeWithOcr(request);
            List<PlateRecognitionResult> results = analysis.results();
            log.info("LPR-OCR: 输入 {} -> 匹配 {} 个车牌, 耗时 {} ms",
                    request.getImgUrl(), results.size(), System.currentTimeMillis() - start);
            return new HttpResult<>(true, results);
        } catch (IOException | OrtException | RuntimeException e) {
            log.error("LPR-OCR 识别失败", e);
            return new HttpResult<>(false, e.getMessage());
        }
    }

    @PostMapping(value = "/v1/lprOcrI", consumes = "application/json", produces = "image/jpeg")
    public Object recognizeWithOcrImage(@RequestBody DetectionRequestWithArea request) {
        long start = System.currentTimeMillis();
        try {
            LprService.AnalysisResult analysis = lprService.analyzeWithOcr(request);
            BufferedImage overlay = lprService.overlay(analysis);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(overlay, "jpg", baos);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.IMAGE_JPEG);
            log.info("LPR-OCR-I: 输入 {} -> 匹配 {} 个车牌, 耗时 {} ms",
                    request.getImgUrl(), analysis.results().size(), System.currentTimeMillis() - start);
            return new ResponseEntity<>(baos.toByteArray(), headers, HttpStatus.OK);
        } catch (IOException | OrtException | RuntimeException e) {
            log.error("LPR-OCR 图像返回失败", e);
            return new HttpResult<>(false, e.getMessage());
        }
    }
}
