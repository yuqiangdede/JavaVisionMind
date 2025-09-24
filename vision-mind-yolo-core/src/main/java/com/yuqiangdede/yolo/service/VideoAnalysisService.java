package com.yuqiangdede.yolo.service;
//
//import lombok.extern.slf4j.Slf4j;
//import org.opencv.core.Mat;
//import org.opencv.videoio.VideoCapture;
//import org.springframework.stereotype.Service;
//import reactor.core.publisher.Flux;
//import reactor.core.scheduler.Schedulers;
//
//import java.util.ArrayList;
//import java.util.List;
//import java.util.concurrent.atomic.AtomicInteger;
//
//
//@Service
//@Slf4j
//public class VideoAnalysisService {
//
//    static {
//        // 加载opencv需要的动态库
//        String osName = System.getProperty("os.name").toLowerCase();
//        if (osName.contains("win")) {
//            System.load(Constant.OPENCV_DLL_PATH);
//        } else if (osName.contains("nix") || osName.contains("nux") || osName.contains("aix")) {
//            System.load(Constant.OPENCV_SO_PATH);
//        } else {
//            throw new UnsupportedOperationException("Unsupported operating system: " + osName);
//        }
//
//    }
//
//    /**
//     * 检测rtsp视频流并返回对象流Flux
//     *
//     * @param rtsp     rtsp视频流地址
//     * @param frameNum 需要分析的视频帧数
//     * @return Flux<Object> 对象流，包含从视频流中检测到的Box列表
//     */
//    public Flux<Object> detect(String rtsp, Integer frameNum, Float conf, String types) {
//        VideoCapture video = new VideoCapture();
//        boolean open = video.open(rtsp);
//        if (!open) {
//            log.error("Failed to open video: {}", rtsp);
//            return Flux.error(new IllegalStateException("Failed to open video"));
//        }
//
//        Mat img = new Mat();
//        AtomicInteger frameCounter = new AtomicInteger(0);
//
//        return Flux.generate(sink->{
//            int currentFrame = frameCounter.incrementAndGet();
//            if (currentFrame > frameNum) { // 当前帧数超过frameNum，结束处理
//                video.release();
//                sink.complete();
//                return;
//            }
//            if (video.read(img)) {
//                if (FRAME_INTERVAL == 0 || currentFrame % FRAME_INTERVAL == 0) {
//                    try {
//                        long start = System.currentTimeMillis();
//                        List<Box> boxes = ImgAnalysisService.analysis(img, conf, types);
//                        sink.next(boxes);
//                        log.info("正在分析第{}帧，分析耗时：{}，boxes: {}", currentFrame, (System.currentTimeMillis() - start), boxes);
//                    } catch (Exception e) {
//                        log.error("Error processing frame: ", e);
//                        video.release();
//                        sink.error(e);
//                    }
//                } else {
//                    log.info("跳过分析第{}帧", currentFrame);
//                    sink.next(new ArrayList<Box>());
//                }
//            } else {
//                video.release();
//                sink.complete();
//            }
//        }).subscribeOn(Schedulers.boundedElastic());
//    }
//
//}
