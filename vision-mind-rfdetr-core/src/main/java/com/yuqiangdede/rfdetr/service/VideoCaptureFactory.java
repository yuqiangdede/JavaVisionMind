package com.yuqiangdede.rfdetr.service;

import org.opencv.videoio.VideoCapture;

@FunctionalInterface
public interface VideoCaptureFactory {

    VideoCapture create();
}
