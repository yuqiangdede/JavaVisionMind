package com.yuqiangdede.rfdetr.service;

import com.yuqiangdede.common.dto.output.Box;
import com.yuqiangdede.rfdetr.config.RfDetrProperties;
import com.yuqiangdede.rfdetr.dto.input.VideoInput;
import com.yuqiangdede.rfdetr.dto.output.VideoFrameDetectionResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.opencv.core.Mat;
import org.opencv.videoio.VideoCapture;
import org.opencv.videoio.Videoio;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class VideoAnalysisService {

    private static final int DEFAULT_FRAME_NUM = 100;

    private final RfDetrImageAnalysisService imageAnalysisService;
    private final VideoCaptureFactory videoCaptureFactory;
    private final RfDetrProperties properties;

    public List<VideoFrameDetectionResult> detect(VideoInput input) {
        if (input == null || !StringUtils.hasText(input.getRtspUrl())) {
            throw new IllegalArgumentException("rtspUrl is null or empty");
        }
        int frameNum = input.getFrameNum() == null || input.getFrameNum() <= 0 ? DEFAULT_FRAME_NUM : input.getFrameNum();
        int frameInterval = input.getFrameInterval() == null || input.getFrameInterval() <= 0
                ? Math.max(1, properties.getFrameInterval()) : input.getFrameInterval();
        VideoCapture capture = videoCaptureFactory.create();
        if (!capture.open(input.getRtspUrl())) {
            capture.release();
            throw new IllegalStateException("Failed to open video: " + input.getRtspUrl());
        }

        Mat frame = createFrameBuffer();
        List<VideoFrameDetectionResult> result = new ArrayList<>();
        try {
            for (int currentFrame = 1; currentFrame <= frameNum; currentFrame++) {
                if (!capture.read(frame)) {
                    break;
                }
                if (frame.empty() || currentFrame % frameInterval != 0) {
                    continue;
                }
                long start = System.currentTimeMillis();
                List<Box> boxes = imageAnalysisService.detectMat(frame, input.getConf(), input.getTypes(),
                        input.getDetectionFrames(), input.getBlockingFrames());
                long timestamp = safeTimestamp(capture.get(Videoio.CAP_PROP_POS_MSEC));
                long cost = System.currentTimeMillis() - start;
                result.add(new VideoFrameDetectionResult(currentFrame, timestamp, cost, boxes));
                log.info("RF-DETR video detect frame={}, ts={}ms, cost={}ms, boxSize={}",
                        currentFrame, timestamp, cost, boxes.size());
            }
        } finally {
            frame.release();
            capture.release();
        }
        return result;
    }

    Mat createFrameBuffer() {
        return new Mat();
    }

    private long safeTimestamp(double timestamp) {
        return Double.isNaN(timestamp) || timestamp < 0 ? 0L : (long) timestamp;
    }
}
