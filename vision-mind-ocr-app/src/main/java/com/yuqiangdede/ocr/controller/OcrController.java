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
            log.info("OCR detect success: request={}, detections={}, cost={}ms", request, detections.size(), System.currentTimeMillis() - start);
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
            byte[] payload = ocrService.detectI(request);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.IMAGE_JPEG);
            log.info("OCR detect-image success: request={}, bytes={}, cost={}ms", request, payload.length, System.currentTimeMillis() - start);
            return new ResponseEntity<>(payload, headers, HttpStatus.OK);
        } catch (IllegalArgumentException ex) {
            log.warn("OCR detect-image validation failed: {}", ex.getMessage());
            return new HttpResult<>(false, ex.getMessage());
        } catch (IOException | OrtException | RuntimeException ex) {
            log.error("OCR detect-image failed", ex);
            return new HttpResult<>(false, ex.getMessage());
        }
    }

    /**
     * 经过大模型进行整段语义重建，会根据语义修正一些错误，但是会丢失原始的坐标信息
     * 文字连贯性强，不关注文字位置的的情况下可以使用
     *
     */
    @PostMapping(value = "/detectWithSR", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public HttpResult<String> detectWithSR(@RequestBody OcrDetectionRequest request) {
        long start = System.currentTimeMillis();
        try {
            String detections = ocrService.detectWithSR(request);
            log.info("OCR detectWithSR success: request={}, detections={}, cost={}ms", request, detections, System.currentTimeMillis() - start);
            return new HttpResult<>(true, detections);
        } catch (IllegalArgumentException ex) {
            log.warn("OCR detectWithSR validation failed: {}", ex.getMessage());
            return new HttpResult<>(false, ex.getMessage());
        } catch (IOException | OrtException | RuntimeException ex) {
            log.error("OCR detectWithSR failed", ex);
            return new HttpResult<>(false, ex.getMessage());
        }
    }

    /**
     * 使用大模型修复语义
     * 本模式很依赖大模型的能力（推荐现场上了DeepSeek V3.1能力以上的大模型再用）
     */
    @PostMapping(value = "/detectWithLLM", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public HttpResult<String> detectWithLLM(@RequestBody OcrDetectionRequest request) {
        long start = System.currentTimeMillis();
        try {
            String detections = ocrService.detectWithLLM(request);
            log.info("OCR detectWithLLM success: request={}, detections={}, cost={}ms", request, detections, System.currentTimeMillis() - start);
            return new HttpResult<>(true, detections);
        } catch (IllegalArgumentException ex) {
            log.warn("OCR detectWithLLM validation failed: {}", ex.getMessage());
            return new HttpResult<>(false, ex.getMessage());
        } catch (IOException | OrtException | RuntimeException ex) {
            log.error("OCR detectWithLLM failed", ex);
            return new HttpResult<>(false, ex.getMessage());
        }
    }

    @PostMapping(value = "/detectWithLLMI", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Object detectWithLLMI(@RequestBody OcrDetectionRequest request) {
        long start = System.currentTimeMillis();
        try {
            byte[] payload = ocrService.detectWithLLMI(request);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.IMAGE_JPEG);
            log.info("OCR detectWithLLMI success: request={}, bytes={}, cost={}ms", request, payload.length, System.currentTimeMillis() - start);
            return new ResponseEntity<>(payload, headers, HttpStatus.OK);
        } catch (IllegalArgumentException ex) {
            log.warn("OCR detectWithLLMI validation failed: {}", ex.getMessage());
            return new HttpResult<>(false, ex.getMessage());
        } catch (IOException | OrtException | RuntimeException ex) {
            log.error("OCR detectWithLLMI failed", ex);
            return new HttpResult<>(false, ex.getMessage());
        }
    }

}
