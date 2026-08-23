package com.yuqiangdede.yolo.config;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ConstantTest {

    @Test
    void nativeLibraryPathIsAbsoluteAndPointsToProjectResource() {
        Path opencvPath = Path.of(Constant.OPENCV_DLL_PATH);

        assertTrue(opencvPath.isAbsolute(), () -> "OpenCV path is not absolute: " + opencvPath);
        assertTrue(Files.isRegularFile(opencvPath), () -> "OpenCV DLL does not exist: " + opencvPath);
    }
}
