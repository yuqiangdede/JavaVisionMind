package com.yuqiangdede.tts.controller;

import com.yuqiangdede.common.dto.output.HttpResult;
import com.yuqiangdede.tts.dto.input.TtsSynthesizeRequest;
import com.yuqiangdede.tts.dto.output.TtsHealthResponse;
import com.yuqiangdede.tts.dto.output.TtsVoicesResponse;
import com.yuqiangdede.tts.service.SherpaOnnxTtsService;
import com.yuqiangdede.tts.service.TtsSynthesisResult;
import com.yuqiangdede.tts.service.WavUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping({"/api/v1/tts", "/api/v1/audio/tts"})
@RequiredArgsConstructor
@Slf4j
public class TtsController {

    private final SherpaOnnxTtsService ttsService;

    @GetMapping(value = {"/health", "/runtime/health"}, produces = MediaType.APPLICATION_JSON_VALUE)
    public HttpResult<TtsHealthResponse> health() {
        try {
            TtsHealthResponse health = ttsService.health();
            return health.isReady() ? new HttpResult<>(true, health) : new HttpResult<>(false, health.getMessage(), health);
        } catch (RuntimeException e) {
            log.error("TTS health failed", e);
            return new HttpResult<>(false, e.getMessage());
        }
    }

    @GetMapping(value = {"/voices", "/index/voices"}, produces = MediaType.APPLICATION_JSON_VALUE)
    public HttpResult<TtsVoicesResponse> voices() {
        try {
            return new HttpResult<>(true, ttsService.voices());
        } catch (RuntimeException e) {
            log.error("Load voices failed", e);
            return new HttpResult<>(false, e.getMessage());
        }
    }

    @PostMapping(value = {"/synthesize", "/infer"}, consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> synthesize(@RequestBody TtsSynthesizeRequest request) {
        long start = System.currentTimeMillis();
        try {
            if (request == null) {
                return ResponseEntity.badRequest()
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(new HttpResult<>(false, "request body must not be empty"));
            }
            TtsSynthesisResult result = ttsService.synthesize(request.getText(), request.getVoice(), request.getSpeed());
            byte[] wavBytes = WavUtils.toWav(result.getSamples(), result.getSampleRate());

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType("audio/wav"));
            headers.setContentLength(wavBytes.length);
            headers.setContentDisposition(ContentDisposition.inline()
                    .filename("tts-" + System.currentTimeMillis() + ".wav", StandardCharsets.UTF_8)
                    .build());
            headers.add("X-TTS-Model", ttsService.getModelId());
            headers.add("X-TTS-Voice", String.valueOf(result.getVoice()));
            headers.add("X-TTS-Sample-Rate", String.valueOf(result.getSampleRate()));
            headers.add("X-TTS-Cost-Ms", String.valueOf(System.currentTimeMillis() - start));

            log.info("TTS synthesize success: voice={} sampleRate={} bytes={} cost={}ms",
                    result.getVoice(), result.getSampleRate(), wavBytes.length, System.currentTimeMillis() - start);
            return new ResponseEntity<>(wavBytes, headers, HttpStatus.OK);
        } catch (IllegalArgumentException e) {
            log.warn("TTS synthesize validation failed: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new HttpResult<>(false, e.getMessage()));
        } catch (RuntimeException e) {
            log.error("TTS synthesize failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new HttpResult<>(false, e.getMessage()));
        }
    }
}
