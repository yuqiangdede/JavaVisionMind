package com.yuqiangdede.yolo.dto.output;

import java.util.List;

public record YoloPoseDetectionResult(List<List<Float>> boxes) {
}