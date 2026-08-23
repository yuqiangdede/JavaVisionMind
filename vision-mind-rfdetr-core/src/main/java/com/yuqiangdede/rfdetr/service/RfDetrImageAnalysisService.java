package com.yuqiangdede.rfdetr.service;

import com.yuqiangdede.common.dto.Point;
import com.yuqiangdede.common.dto.output.Box;
import com.yuqiangdede.common.util.GeometryUtils;
import com.yuqiangdede.common.util.ImageUtil;
import com.yuqiangdede.rfdetr.config.RfDetrProperties;
import com.yuqiangdede.rfdetr.dto.input.DetectionRequestWithArea;
import com.yuqiangdede.rfdetr.runtime.CocoClassNames;
import com.yuqiangdede.rfdetr.runtime.RfDetrInferenceEngine;
import lombok.RequiredArgsConstructor;
import org.opencv.core.Mat;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class RfDetrImageAnalysisService {

    private final RfDetrInferenceEngine inferenceEngine;
    private final RfDetrProperties properties;

    public List<Box> detectArea(DetectionRequestWithArea request) throws IOException {
        BufferedImage image = ImageUtil.urlToImage(request.getImgUrl());
        return detect(image, request.getThreshold(), request.getTypes(), request.getDetectionFrames(), request.getBlockingFrames());
    }

    public BufferedImage detectAreaPreview(DetectionRequestWithArea request) throws IOException {
        BufferedImage image = ImageUtil.urlToImage(request.getImgUrl());
        List<Box> boxes = detect(image, request.getThreshold(), request.getTypes(),
                request.getDetectionFrames(), request.getBlockingFrames());
        ImageUtil.drawImageWithBox(image, boxes);
        ImageUtil.drawImageWithFrames(image, copyFrames(request.getDetectionFrames()), Color.BLUE);
        ImageUtil.drawImageWithFrames(image, copyFrames(request.getBlockingFrames()), Color.DARK_GRAY);
        return image;
    }

    public List<Box> detectUpload(BufferedImage image, Float threshold, String types) {
        return detect(image, threshold, types, null, null);
    }

    public List<Box> detectMat(Mat frame, Float threshold, String types,
                               ArrayList<ArrayList<Point>> detectionFrames,
                               ArrayList<ArrayList<Point>> blockingFrames) {
        if (frame == null || frame.empty()) {
            throw new IllegalArgumentException("video frame is null or empty");
        }
        return detect(ImageUtil.matToBufferedImage(frame), threshold, types, detectionFrames, blockingFrames);
    }

    private List<Box> detect(BufferedImage image, Float threshold, String types,
                             ArrayList<ArrayList<Point>> detectionFrames,
                             ArrayList<ArrayList<Point>> blockingFrames) {
        List<Box> boxes = inferenceEngine.detect(image, threshold);
        List<Integer> acceptedTypes = parseTypes(types);
        List<Box> typed = boxes.stream().filter(box -> acceptedTypes.contains(box.getType())).toList();
        return filterByFrames(typed, detectionFrames, blockingFrames);
    }

    private List<Integer> parseTypes(String types) {
        String resolved = types == null || types.isBlank() ? properties.getDefaultTypes() : types;
        List<Integer> result = Arrays.stream(resolved.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(Integer::parseInt)
                .toList();
        if (result.isEmpty()) {
            throw new IllegalArgumentException("types is empty");
        }
        for (Integer type : result) {
            if (!CocoClassNames.standard().containsKey(type)) {
                throw new IllegalArgumentException("unsupported RF-DETR COCO class id: " + type);
            }
        }
        return result;
    }

    private List<Box> filterByFrames(List<Box> boxes, ArrayList<ArrayList<Point>> detectionFrames,
                                     ArrayList<ArrayList<Point>> blockingFrames) {
        Set<Box> result = new LinkedHashSet<>();
        List<ArrayList<Point>> detections = copyFrames(detectionFrames);
        if (detections.isEmpty()) {
            result.addAll(boxes);
        } else {
            for (Box box : boxes) {
                for (ArrayList<Point> frame : detections) {
                    if (GeometryUtils.calcOverlap(box, frame) > properties.getDetectionRatio()) {
                        result.add(box);
                        break;
                    }
                }
            }
        }

        for (Box box : List.copyOf(result)) {
            for (ArrayList<Point> frame : copyFrames(blockingFrames)) {
                if (GeometryUtils.calcOverlap(box, frame) > properties.getBlockingRatio()) {
                    result.remove(box);
                    break;
                }
            }
        }
        return new ArrayList<>(result);
    }

    private ArrayList<ArrayList<Point>> copyFrames(List<? extends List<Point>> frames) {
        ArrayList<ArrayList<Point>> copied = new ArrayList<>();
        if (frames == null) {
            return copied;
        }
        for (List<Point> frame : frames) {
            if (frame == null || frame.size() < 3) {
                throw new IllegalArgumentException("each detection frame must contain at least three points");
            }
            ArrayList<Point> points = new ArrayList<>();
            for (Point point : frame) {
                if (point == null || point.getX() == null || point.getY() == null) {
                    throw new IllegalArgumentException("detection frame contains an invalid point");
                }
                points.add(new Point(point.getX(), point.getY()));
            }
            copied.add(points);
        }
        return copied;
    }
}
