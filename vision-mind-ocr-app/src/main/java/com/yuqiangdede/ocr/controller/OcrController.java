package com.yuqiangdede.ocr.controller;

import ai.onnxruntime.OrtException;
import com.yuqiangdede.common.dto.output.HttpResult;
import com.yuqiangdede.ocr.dto.input.OcrDetectionRequest;
import com.yuqiangdede.ocr.dto.output.OcrDetectionResult;
import com.yuqiangdede.ocr.service.OcrService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/v1/ocr")
@RequiredArgsConstructor
@Slf4j
public class OcrController {

    private final OcrService ocrService;

    @PostMapping(value = "/detect", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public HttpResult<List<OcrDetectionResult>> detect(@RequestBody OcrDetectionRequest request) {
        long start = System.currentTimeMillis();
        try {
            List<OcrDetectionResult> detections = ocrService.detect(request);
            log.info("OCR detect success: request={}, detections={}, cost={}ms",
                    request, detections.size(), System.currentTimeMillis() - start);
            return new HttpResult<>(true, detections);
        } catch (IllegalArgumentException ex) {
            log.warn("OCR detect validation failed: {}", ex.getMessage());
            return new HttpResult<>(false, ex.getMessage());
        } catch (IOException | OrtException | RuntimeException ex) {
            log.error("OCR detect failed", ex);
            return new HttpResult<>(false, ex.getMessage());
        }
    }

    @PostMapping(value = "/detectI", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.IMAGE_JPEG_VALUE)
    public Object detectImage(@RequestBody OcrDetectionRequest request) {
        long start = System.currentTimeMillis();
        try {
            byte[] payload = ocrService.detectWithOverlayBytes(request);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.IMAGE_JPEG);
            log.info("OCR detect-image success: request={}, bytes={}, cost={}ms",
                    request, payload.length, System.currentTimeMillis() - start);
            return new ResponseEntity<>(payload, headers, HttpStatus.OK);
        } catch (IllegalArgumentException ex) {
            log.warn("OCR detect-image validation failed: {}", ex.getMessage());
            return new HttpResult<>(false, ex.getMessage());
        } catch (IOException | OrtException | RuntimeException ex) {
            log.error("OCR detect-image failed", ex);
            return new HttpResult<>(false, ex.getMessage());
        }
    }
}
