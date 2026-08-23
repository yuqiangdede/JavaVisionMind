package com.yuqiangdede.rfdetr.controller;

import com.yuqiangdede.common.dto.output.HttpResult;
import com.yuqiangdede.rfdetr.dto.input.VideoInput;
import com.yuqiangdede.rfdetr.dto.output.VideoFrameDetectionResult;
import com.yuqiangdede.rfdetr.service.VideoAnalysisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Slf4j
@RequestMapping("/api")
@RequiredArgsConstructor
public class VideoAnalysisController {

    private final VideoAnalysisService videoAnalysisService;

    @PostMapping(value = {"/v1/video/detect", "/v1/vision/video/detect"}, consumes = "application/json", produces = "application/json")
    public HttpResult<List<VideoFrameDetectionResult>> detect(@RequestBody VideoInput request) {
        if (request == null || !StringUtils.hasText(request.getRtspUrl())) {
            return new HttpResult<>(false, "rtspUrl is null or empty");
        }
        try {
            List<VideoFrameDetectionResult> frames = videoAnalysisService.detect(request);
            return new HttpResult<>(true, frames);
        } catch (RuntimeException ex) {
            log.error("RF-DETR video detect failed", ex);
            return new HttpResult<>(false, ex.getMessage());
        }
    }
}
