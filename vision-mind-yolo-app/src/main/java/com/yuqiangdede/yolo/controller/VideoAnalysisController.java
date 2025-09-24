//package com.yuqiangdede.yolo.controller;
//
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.util.ObjectUtils;
//import org.springframework.web.bind.annotation.*;
//import org.springframework.http.MediaType;
//import reactor.core.publisher.Flux;
//
//@RestController
//@Slf4j
//@RequestMapping("/api")
//public class VideoAnalysisController {
//    @Autowired
//    private VideoAnalysisService videoAnalysisService;
//
//
//    /**
//     * 视频检测，然后长时间运行还有问题,linux下不行，不知道是不是ffmpeg的问题
//     *
//     * @param videoInput rtsp地址、视频帧数
//     * @return 流式输出结果
//     */
//    @PostMapping(value = "/v1/video/detect", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
//    public Flux<Object> videoPredictor(@RequestBody VideoInput videoInput) {
//        if (ObjectUtils.isEmpty(videoInput.getRtspUrl())) {
//            return Flux.error(new IllegalArgumentException("rtspUrl is null or empty"));
//        }
//        // 默认给100帧
//        Integer frameNum = ObjectUtils.isEmpty(videoInput.getFrameNum()) ? Integer.valueOf(100) : videoInput.getFrameNum();
//
//        Flux<Object> detect = videoAnalysisService.detect(videoInput.getRtspUrl(), frameNum, videoInput.getConf(), videoInput.getTypes());
//        return detect
//                .doOnError(error->log.error("Error in stream", error))
//                .doOnComplete(()->log.info("Stream completed"));
//    }
//
//
//}
