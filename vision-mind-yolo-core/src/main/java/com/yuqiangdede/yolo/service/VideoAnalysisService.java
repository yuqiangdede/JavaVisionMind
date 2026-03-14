package com.yuqiangdede.yolo.service;

import com.yuqiangdede.common.dto.output.Box;
import com.yuqiangdede.yolo.config.Constant;
import com.yuqiangdede.yolo.dto.input.VideoInput;
import com.yuqiangdede.yolo.dto.output.VideoFrameDetectionResult;
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

    private final ImgAnalysisService imgAnalysisService;
    private final VideoCaptureFactory videoCaptureFactory;

    /**
     * 使用OpenCV读取视频流并逐帧检测，返回每个采样帧的检测结果。
     */
    public List<VideoFrameDetectionResult> detect(VideoInput videoInput) {
        if (videoInput == null || !StringUtils.hasText(videoInput.getRtspUrl())) {
            throw new IllegalArgumentException("rtspUrl is null or empty");
        }
        String source = videoInput.getRtspUrl();
        int frameNum = resolveFrameNum(videoInput.getFrameNum());
        int frameInterval = resolveFrameInterval(videoInput.getFrameInterval());

        VideoCapture capture = videoCaptureFactory.create();
        if (!capture.open(source)) {
            capture.release();
            throw new IllegalStateException("Failed to open video: " + source);
        }

        Mat frame = createFrameBuffer();
        List<VideoFrameDetectionResult> result = new ArrayList<>();
        try {
            for (int currentFrame = 1; currentFrame <= frameNum; currentFrame++) {
                if (!capture.read(frame)) {
                    break;
                }
                if (frame.empty()) {
                    continue;
                }
                if (!shouldAnalyze(currentFrame, frameInterval)) {
                    continue;
                }

                long start = System.currentTimeMillis();
                List<Box> boxes = imgAnalysisService.detectMat(
                        frame,
                        videoInput.getConf(),
                        videoInput.getTypes(),
                        videoInput.getDetectionFrames(),
                        videoInput.getBlockingFrames()
                );
                long cost = System.currentTimeMillis() - start;
                long timestampMs = safeTimestamp(capture.get(Videoio.CAP_PROP_POS_MSEC));

                result.add(new VideoFrameDetectionResult(currentFrame, timestampMs, cost, boxes));
                log.info("Video detect frame={}, ts={}ms, cost={}ms, boxSize={}",
                        currentFrame, timestampMs, cost, boxes.size());
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

    private int resolveFrameNum(Integer frameNum) {
        if (frameNum == null || frameNum <= 0) {
            return DEFAULT_FRAME_NUM;
        }
        return frameNum;
    }

    private int resolveFrameInterval(Integer frameInterval) {
        if (frameInterval == null || frameInterval <= 0) {
            return Constant.FRAME_INTERVAL == null || Constant.FRAME_INTERVAL <= 0 ? 1 : Constant.FRAME_INTERVAL;
        }
        return frameInterval;
    }

    private boolean shouldAnalyze(int frame, int frameInterval) {
        if (frameInterval <= 1) {
            return true;
        }
        return frame % frameInterval == 0;
    }

    private long safeTimestamp(double timestamp) {
        if (Double.isNaN(timestamp) || timestamp < 0) {
            return 0L;
        }
        return (long) timestamp;
    }
}
