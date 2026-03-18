package com.yuqiangdede.asr.controller;

import com.yuqiangdede.asr.dto.input.AsrSourceTranscribeRequest;
import com.yuqiangdede.asr.dto.input.HotwordConfigUpdateRequest;
import com.yuqiangdede.asr.dto.input.PhraseRuleConfigUpdateRequest;
import com.yuqiangdede.asr.dto.output.AsrHealthResponse;
import com.yuqiangdede.asr.dto.output.AsrTranscribeResponse;
import com.yuqiangdede.asr.dto.output.HotwordConfigResponse;
import com.yuqiangdede.asr.dto.output.PhraseRuleConfigResponse;
import com.yuqiangdede.asr.service.AsrConfigService;
import com.yuqiangdede.asr.service.AsrService;
import com.yuqiangdede.common.dto.output.HttpResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.net.URI;
import java.util.List;
import java.util.Base64;

@RestController
@RequestMapping({"/api/v1/asr", "/api/v1/audio/asr"})
@RequiredArgsConstructor
@Slf4j
public class AsrController {

    private final AsrService asrService;
    private final AsrConfigService asrConfigService;

    @GetMapping(value = {"/health", "/runtime/health"}, produces = MediaType.APPLICATION_JSON_VALUE)
    public HttpResult<AsrHealthResponse> health() {
        try {
            return new HttpResult<>(true, asrService.health());
        } catch (RuntimeException e) {
            log.error("ASR health failed", e);
            return new HttpResult<>(false, e.getMessage());
        }
    }

    @GetMapping(value = {"/hotwords", "/store/hotwords"}, produces = MediaType.APPLICATION_JSON_VALUE)
    public HttpResult<HotwordConfigResponse> hotwords() {
        try {
            return new HttpResult<>(true, new HotwordConfigResponse(asrConfigService.getBaseHotwords()));
        } catch (RuntimeException e) {
            log.error("Load hotwords failed", e);
            return new HttpResult<>(false, e.getMessage());
        }
    }

    @PostMapping(value = {"/hotwords", "/store/hotwords"}, consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public HttpResult<HotwordConfigResponse> saveHotwords(@RequestBody HotwordConfigUpdateRequest request) {
        try {
            return new HttpResult<>(true, new HotwordConfigResponse(asrConfigService.saveBaseHotwords(request.getBaseTerms())));
        } catch (RuntimeException e) {
            log.error("Save hotwords failed", e);
            return new HttpResult<>(false, e.getMessage());
        }
    }

    @GetMapping(value = {"/phrase-rules", "/index/phrase-rules"}, produces = MediaType.APPLICATION_JSON_VALUE)
    public HttpResult<PhraseRuleConfigResponse> phraseRules() {
        try {
            return new HttpResult<>(true, new PhraseRuleConfigResponse(asrConfigService.getPhraseRules()));
        } catch (RuntimeException e) {
            log.error("Load phrase rules failed", e);
            return new HttpResult<>(false, e.getMessage());
        }
    }

    @PostMapping(value = {"/phrase-rules", "/index/phrase-rules"}, consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public HttpResult<PhraseRuleConfigResponse> savePhraseRules(@RequestBody PhraseRuleConfigUpdateRequest request) {
        try {
            return new HttpResult<>(true, new PhraseRuleConfigResponse(asrConfigService.savePhraseRules(request.getLines())));
        } catch (IllegalArgumentException e) {
            log.warn("Save phrase rules validation failed: {}", e.getMessage());
            return new HttpResult<>(false, e.getMessage());
        } catch (RuntimeException e) {
            log.error("Save phrase rules failed", e);
            return new HttpResult<>(false, e.getMessage());
        }
    }

    @PostMapping(value = {"/transcribe", "/infer", "/search/transcribe"}, consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public HttpResult<AsrTranscribeResponse> transcribe(@RequestParam("file") MultipartFile file,
                                                        @RequestParam(value = "enablePunctuation", defaultValue = "false") boolean enablePunctuation) {
        long start = System.currentTimeMillis();
        try {
            AsrTranscribeResponse response = asrService.transcribe(file, enablePunctuation);
            log.info("ASR transcribe success: file={} cost={}ms", file.getOriginalFilename(), System.currentTimeMillis() - start);
            return new HttpResult<>(true, response);
        } catch (IllegalArgumentException e) {
            log.warn("ASR transcribe validation failed: {}", e.getMessage());
            return new HttpResult<>(false, e.getMessage());
        } catch (RuntimeException e) {
            log.error("ASR transcribe failed", e);
            return new HttpResult<>(false, e.getMessage());
        }
    }

    @PostMapping(value = "/transcribe/source", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public HttpResult<AsrTranscribeResponse> transcribeSource(@RequestBody AsrSourceTranscribeRequest request) {
        try {
            boolean enablePunctuation = request.getEnablePunctuation() != null && request.getEnablePunctuation();
            if (request.getAudioBase64() != null && !request.getAudioBase64().isBlank()) {
                String payload = request.getAudioBase64();
                int comma = payload.indexOf(',');
                if (payload.startsWith("data:") && comma > 0) {
                    payload = payload.substring(comma + 1);
                }
                byte[] bytes = Base64.getDecoder().decode(payload);
                return new HttpResult<>(true, asrService.transcribe(bytes, request.getFileName(), "audio/wav", enablePunctuation));
            }
            if (request.getAudioUrl() != null && !request.getAudioUrl().isBlank()) {
                URI uri = URI.create(request.getAudioUrl());
                try (InputStream inputStream = uri.toURL().openStream()) {
                    byte[] bytes = inputStream.readAllBytes();
                    return new HttpResult<>(true, asrService.transcribe(bytes, request.getFileName(), "audio/wav", enablePunctuation));
                }
            }
            return new HttpResult<>(false, "audioUrl/audioBase64 is empty");
        } catch (Exception ex) {
            log.error("ASR transcribe source failed", ex);
            return new HttpResult<>(false, ex.getMessage());
        }
    }
}
