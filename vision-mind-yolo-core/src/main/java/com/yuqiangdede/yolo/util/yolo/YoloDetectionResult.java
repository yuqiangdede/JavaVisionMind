package com.yuqiangdede.yolo.util.yolo;

import java.util.List;
import java.util.Map;

public record YoloDetectionResult(List<List<Float>> boxes, Map<Integer, String> classNames) {
}