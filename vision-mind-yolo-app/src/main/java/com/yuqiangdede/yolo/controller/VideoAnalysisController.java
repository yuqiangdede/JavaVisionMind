//package com.yuqiangdede.yolo.controller;
//
//import com.yuqiangdede.yolo.dto.input.VideoInput;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.http.codec.ServerSentEvent;
//import org.springframework.util.ObjectUtils;
//import org.springframework.web.bind.annotation.*;
//import org.springframework.http.MediaType;
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
//    @PostMapping(
//            value = "/v1/video/detect",
//            consumes = MediaType.APPLICATION_JSON_VALUE,
//            produces = MediaType.TEXT_EVENT_STREAM_VALUE
//    )
//    public Flux<ServerSentEvent<DetectionResult>> videoPredictor(@RequestBody VideoInput videoInput) {
//        if (ObjectUtils.isEmpty(videoInput.getRtspUrl())) {
//            return Flux.error(new IllegalArgumentException("rtspUrl is null or empty"));
//        }
//        int frameNum = ObjectUtils.isEmpty(videoInput.getFrameNum()) ? 100 : videoInput.getFrameNum();
//        return videoAnalysisService.detect(videoInput.getRtspUrl(), frameNum, videoInput.getConf(), videoInput.getTypes())
//                .map(result -> ServerSentEvent.<DetectionResult>builder()
//                        .event("detection")
//                        .data(result)
//                        .build())
//                .doOnError(error -> log.error("Error in stream", error))
//                .doOnCancel(() -> log.info("Client closed connection"));
//    }
//
//
//}
