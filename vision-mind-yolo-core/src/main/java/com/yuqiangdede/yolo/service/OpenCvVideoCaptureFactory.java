package com.yuqiangdede.yolo.service;

import org.opencv.videoio.VideoCapture;
import org.springframework.stereotype.Component;

@Component
public class OpenCvVideoCaptureFactory implements VideoCaptureFactory {

    @Override
    public VideoCapture create() {
        return new VideoCapture();
    }
}
