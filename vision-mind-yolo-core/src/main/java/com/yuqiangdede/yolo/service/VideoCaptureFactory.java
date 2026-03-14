package com.yuqiangdede.yolo.service;

import org.opencv.videoio.VideoCapture;

@FunctionalInterface
public interface VideoCaptureFactory {

    VideoCapture create();
}
