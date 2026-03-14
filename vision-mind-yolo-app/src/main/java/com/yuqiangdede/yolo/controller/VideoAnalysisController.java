package com.yuqiangdede.yolo.controller;

import com.yuqiangdede.common.dto.output.HttpResult;
import com.yuqiangdede.yolo.dto.input.VideoInput;
import com.yuqiangdede.yolo.dto.output.VideoFrameDetectionResult;
import com.yuqiangdede.yolo.service.VideoAnalysisService;
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

    /**
     * 视频检测（最小可用实现）：按配置帧间隔分析固定数量帧并同步返回结果。
     */
    @PostMapping(value = "/v1/video/detect", produces = "application/json", consumes = "application/json")
    public HttpResult<List<VideoFrameDetectionResult>> videoPredictor(@RequestBody VideoInput videoInput) {
        long startTime = System.currentTimeMillis();
        if (videoInput == null || !StringUtils.hasText(videoInput.getRtspUrl())) {
            return new HttpResult<>(false, "rtspUrl is null or empty");
        }
        try {
            List<VideoFrameDetectionResult> detections = videoAnalysisService.detect(videoInput);
            log.info("Video detect: input={}, sampledFrames={}, cost={}ms",
                    videoInput, detections.size(), System.currentTimeMillis() - startTime);
            return new HttpResult<>(true, detections);
        } catch (RuntimeException e) {
            log.error("video detect error", e);
            return new HttpResult<>(false, e.getMessage());
        }
    }
}
