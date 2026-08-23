package com.yuqiangdede.rfdetr.service;

import com.yuqiangdede.platform.common.resource.ResourcePathResolver;
import com.yuqiangdede.platform.common.runtime.NativeLibraryManager;
import lombok.RequiredArgsConstructor;
import org.opencv.videoio.VideoCapture;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OpenCvVideoCaptureFactory implements VideoCaptureFactory {

    private final NativeLibraryManager nativeLibraryManager;
    private final ResourcePathResolver resourcePathResolver;
    private volatile boolean nativeLoaded;

    @Override
    public VideoCapture create() {
        ensureNativeLoaded();
        return new VideoCapture();
    }

    private synchronized void ensureNativeLoaded() {
        if (nativeLoaded) {
            return;
        }
        nativeLibraryManager.loadOpenCv(
                resourcePathResolver.resolve("lib/opencv/opencv_java490.dll").toString(),
                resourcePathResolver.resolve("lib/opencv/libopencv_java4100.so").toString()
        );
        nativeLoaded = true;
    }
}
