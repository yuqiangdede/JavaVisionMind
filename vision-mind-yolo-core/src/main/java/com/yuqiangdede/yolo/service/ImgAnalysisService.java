package com.yuqiangdede.yolo.service;

import ai.onnxruntime.OrtException;
import com.yuqiangdede.common.dto.Point;
import com.yuqiangdede.yolo.dto.input.DetectionRequest;
import com.yuqiangdede.yolo.dto.input.DetectionRequestWithArea;
import com.yuqiangdede.common.dto.output.Box;
import com.yuqiangdede.common.dto.output.BoxWithKeypoints;
import com.yuqiangdede.common.util.GeometryUtils;
import com.yuqiangdede.common.util.ImageUtil;


import com.yuqiangdede.yolo.dto.output.SegDetection;
import com.yuqiangdede.yolo.util.yolo.YoloFastSAMUtil;
import com.yuqiangdede.yolo.util.yolo.YoloDetectionResult;
import com.yuqiangdede.yolo.util.yolo.YoloPoseDetectionResult;
import com.yuqiangdede.yolo.util.yolo.YoloV11PoseUtil;
import com.yuqiangdede.yolo.util.yolo.YoloV11SegUtil;
import com.yuqiangdede.yolo.util.yolo.YoloV11Util;
import com.yuqiangdede.yolo.config.Constant;
import lombok.extern.slf4j.Slf4j;
import org.opencv.core.Mat;
import org.springframework.stereotype.Service;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;


@Service
@Slf4j
public class ImgAnalysisService {

    static {
        boolean skipProperty = Boolean.parseBoolean(System.getProperty("vision-mind.skip-opencv", "false"));
        boolean testEnv = isTestEnvironment();
        boolean skipLoad = skipProperty || testEnv;
        log.info("OpenCV native load check - skipProperty={}, testEnv={}", skipProperty, testEnv);
        if (skipLoad) {
            log.warn("Skipping OpenCV native library load because tests or configuration requested it");
        } else {
            String osName = System.getProperty("os.name").toLowerCase();
            try {
                if (osName.contains("win")) {
                    System.load(Constant.OPENCV_DLL_PATH);
                } else if (osName.contains("nix") || osName.contains("nux") || osName.contains("aix")) {
                    System.load(Constant.OPENCV_SO_PATH);
                } else {
                    throw new UnsupportedOperationException("Unsupported operating system: " + osName);
                }
            } catch (UnsatisfiedLinkError e) {
                throw new IllegalStateException("Failed to load OpenCV native library", e);
            }
        }
    }

    private static boolean isTestEnvironment() {
        try {
            Class.forName("org.junit.jupiter.api.Test");
            return true;
        } catch (ClassNotFoundException ex) {
            return false;
        }
    }


    public List<Box> detectArea(DetectionRequestWithArea imgAreaInput) throws IOException, OrtException {
        Mat mat = ImageUtil.urlToMat(imgAreaInput.getImgUrl());

        List<Box> boxs = analysis(mat, imgAreaInput.getThreshold(), imgAreaInput.getTypes());
        Set<Box> result = new LinkedHashSet<>();

        if (imgAreaInput.getDetectionFrames() == null || imgAreaInput.getDetectionFrames().isEmpty()) {
            result.addAll(boxs);
        } else {
            for (Box box : boxs) {
                for (ArrayList<Point> detectionFrame : imgAreaInput.getDetectionFrames()) {
                    double detectRatio = GeometryUtils.calcOverlap(box, detectionFrame);
                    if (detectRatio > Constant.DETECT_RATIO) {
                        result.add(box);
                        break;
                    }
                }
            }
        }


        if (imgAreaInput.getBlockingFrames() == null || imgAreaInput.getBlockingFrames().isEmpty()) {
            return new ArrayList<>(result);
        } else {
            Set<Box> toRemove = new HashSet<>();
            for (Box box : boxs) {
                for (ArrayList<Point> blockingFrame : imgAreaInput.getBlockingFrames()) {
                    double blockRatio = GeometryUtils.calcOverlap(box, blockingFrame);
                    if (blockRatio > Constant.BLOCK_RATIO) {
                        toRemove.add(box);
                        break;
                    }
                }
            }
            result.removeAll(toRemove);
        }

        return new ArrayList<>(result);
    }

    public BufferedImage detectAreaI(DetectionRequestWithArea imgAreaInput) throws IOException, OrtException {
        BufferedImage image = ImageUtil.urlToImage(imgAreaInput.getImgUrl());

        List<Box> boxs = this.detectArea(imgAreaInput);

        ImageUtil.drawImageWithBox(image, boxs);
        ImageUtil.drawImageWithFrames(image, imgAreaInput.getDetectionFrames(), Color.BLUE);
        ImageUtil.drawImageWithFrames(image, imgAreaInput.getBlockingFrames(), Color.DARK_GRAY);
        return image;
    }

    private List<Box> analysis(Mat mat, Float conf, String types) {
        YoloDetectionResult detection = YoloV11Util.predictor(mat, conf);
        List<List<Float>> bs = detection.boxes();
        Map<Integer, String> classNames = detection.classNames();

        List<Box> boxes = new ArrayList<>();
        for (List<Float> bx : bs) {
            Box box = new Box(bx.get(0), bx.get(1), bx.get(2), bx.get(3), bx.get(4), bx.get(5), classNames);
            if (types == null || types.isEmpty()) {
                if (Constant.YOLO_TYPES.contains(box.getType())) {
                    boxes.add(box);
                }
            } else {
                List<Integer> typeList = Arrays.stream(types.split(","))
                        .map(Integer::parseInt)
                        .collect(Collectors.toList());

                if (typeList.contains(box.getType())) {
                    boxes.add(box);
                }
            }
        }

        return boxes;
    }


    public List<BoxWithKeypoints> poseArea(DetectionRequestWithArea imgAreaInput) throws IOException, OrtException {
        Mat mat = ImageUtil.urlToMat(imgAreaInput.getImgUrl());

        List<BoxWithKeypoints> boxs = analysisPose(mat, imgAreaInput.getThreshold());
        Set<BoxWithKeypoints> result = new LinkedHashSet<>();

        if (imgAreaInput.getDetectionFrames() == null || imgAreaInput.getDetectionFrames().isEmpty()) {
            result.addAll(boxs);
        } else {
            for (BoxWithKeypoints box : boxs) {
                for (ArrayList<Point> detectionFrame : imgAreaInput.getDetectionFrames()) {
                    double detectRatio = GeometryUtils.calcOverlap(box, detectionFrame);
                    if (detectRatio > Constant.DETECT_RATIO) {
                        result.add(box);
                        break;
                    }
                }
            }
        }

        if (imgAreaInput.getBlockingFrames() == null || imgAreaInput.getBlockingFrames().isEmpty()) {
            return new ArrayList<>(result);
        } else {
            Set<BoxWithKeypoints> toRemove = new HashSet<>();
            for (BoxWithKeypoints box : boxs) {
                for (ArrayList<Point> blockingFrame : imgAreaInput.getBlockingFrames()) {
                    double blockRatio = GeometryUtils.calcOverlap(box, blockingFrame);
                    if (blockRatio > Constant.BLOCK_RATIO) {
                        toRemove.add(box);
                        break;
                    }
                }
            }
            result.removeAll(toRemove);
        }

        return new ArrayList<>(result);
    }

    public BufferedImage poseAreaI(DetectionRequestWithArea imgAreaInput) throws IOException, OrtException {
        BufferedImage image = ImageUtil.urlToImage(imgAreaInput.getImgUrl());

        List<BoxWithKeypoints> boxs = this.poseArea(imgAreaInput);

        ImageUtil.drawImageWithKeypoints(image, boxs);
        ImageUtil.drawImageWithFrames(image, imgAreaInput.getDetectionFrames(), Color.BLUE);
        ImageUtil.drawImageWithFrames(image, imgAreaInput.getBlockingFrames(), Color.DARK_GRAY);
        return image;
    }

    private List<BoxWithKeypoints> analysisPose(Mat mat, Float conf) {
        YoloPoseDetectionResult detection = YoloV11PoseUtil.predictor(mat, conf);
        List<List<Float>> bs = detection.boxes();

        List<BoxWithKeypoints> boxes = new ArrayList<>();
        for (List<Float> bx : bs) {
            BoxWithKeypoints box = new BoxWithKeypoints(bx.get(0), bx.get(1), bx.get(2), bx.get(3), bx.get(4));
            box.injectKeypoints(bx.subList(5, 56), mat.height(), mat.width());

            boxes.add(box);
        }

        return boxes;
    }

    public List<Box> detectFace(DetectionRequestWithArea imgAreaInput) throws IOException, OrtException {
        Mat mat = ImageUtil.urlToMat(imgAreaInput.getImgUrl());

        List<Box> boxs = analysisFace(mat, imgAreaInput.getThreshold());
        Set<Box> result = new LinkedHashSet<>();

        if (imgAreaInput.getDetectionFrames() == null || imgAreaInput.getDetectionFrames().isEmpty()) {
            result.addAll(boxs);
        } else {
            for (Box box : boxs) {
                for (ArrayList<Point> detectionFrame : imgAreaInput.getDetectionFrames()) {
                    double detectRatio = GeometryUtils.calcOverlap(box, detectionFrame);
                    if (detectRatio > Constant.DETECT_RATIO) {
                        result.add(box);
                        break;
                    }
                }
            }
        }


        if (imgAreaInput.getBlockingFrames() == null || imgAreaInput.getBlockingFrames().isEmpty()) {
            return new ArrayList<>(result);
        } else {
            Set<Box> toRemove = new HashSet<>();
            for (Box box : boxs) {
                for (ArrayList<Point> blockingFrame : imgAreaInput.getBlockingFrames()) {
                    double blockRatio = GeometryUtils.calcOverlap(box, blockingFrame);
                    if (blockRatio > Constant.BLOCK_RATIO) {
                        toRemove.add(box);
                        break;
                    }
                }
            }
            result.removeAll(toRemove);
        }

        return new ArrayList<>(result);
    }

    public BufferedImage detectFaceI(DetectionRequestWithArea imgAreaInput) throws IOException, OrtException {
        BufferedImage image = ImageUtil.urlToImage(imgAreaInput.getImgUrl());

        List<Box> boxs = this.detectFace(imgAreaInput);

        ImageUtil.drawImageWithBox(image, boxs);
        ImageUtil.drawImageWithFrames(image, imgAreaInput.getDetectionFrames(), Color.BLUE);
        ImageUtil.drawImageWithFrames(image, imgAreaInput.getBlockingFrames(), Color.DARK_GRAY);
        return image;
    }

    private List<Box> analysisFace(Mat mat, Float conf) {
        YoloDetectionResult detection = YoloV11Util.predictorFace(mat, conf);

        List<List<Float>> bs = detection.boxes();
        Map<Integer, String> classNames = detection.classNames();

        List<Box> boxes = new ArrayList<>();
        for (List<Float> bx : bs) {
            Box box = new Box(bx.get(0), bx.get(1), bx.get(2), bx.get(3), bx.get(4), bx.get(5), classNames);
            boxes.add(box);
        }

        return boxes;
    }

    public List<Box> detectLP(DetectionRequestWithArea imgAreaInput) throws IOException, OrtException {
        Mat mat = ImageUtil.urlToMat(imgAreaInput.getImgUrl());

        List<Box> boxs = analysisLicensePlate(mat, imgAreaInput.getThreshold());
        Set<Box> result = new LinkedHashSet<>();

        if (imgAreaInput.getDetectionFrames() == null || imgAreaInput.getDetectionFrames().isEmpty()) {
            result.addAll(boxs);
        } else {
            for (Box box : boxs) {
                for (ArrayList<Point> detectionFrame : imgAreaInput.getDetectionFrames()) {
                    double detectRatio = GeometryUtils.calcOverlap(box, detectionFrame);
                    if (detectRatio > Constant.DETECT_RATIO) {
                        result.add(box);
                        break;
                    }
                }
            }
        }


        if (imgAreaInput.getBlockingFrames() == null || imgAreaInput.getBlockingFrames().isEmpty()) {
            return new ArrayList<>(result);
        } else {
            Set<Box> toRemove = new HashSet<>();
            for (Box box : boxs) {
                for (ArrayList<Point> blockingFrame : imgAreaInput.getBlockingFrames()) {
                    double blockRatio = GeometryUtils.calcOverlap(box, blockingFrame);
                    if (blockRatio > Constant.BLOCK_RATIO) {
                        toRemove.add(box);
                        break;
                    }
                }
            }
            result.removeAll(toRemove);
        }

        return new ArrayList<>(result);
    }

    public BufferedImage detectLPI(DetectionRequestWithArea imgAreaInput) throws IOException, OrtException {
        BufferedImage image = ImageUtil.urlToImage(imgAreaInput.getImgUrl());

        List<Box> boxs = this.detectLP(imgAreaInput);

        ImageUtil.drawImageWithBox(image, boxs);
        ImageUtil.drawImageWithFrames(image, imgAreaInput.getDetectionFrames(), Color.BLUE);
        ImageUtil.drawImageWithFrames(image, imgAreaInput.getBlockingFrames(), Color.DARK_GRAY);
        return image;
    }

    private List<Box> analysisLicensePlate(Mat mat, Float conf) {
        YoloDetectionResult detection = YoloV11Util.predictorLicensePlate(mat, conf);

        List<List<Float>> bs = detection.boxes();
        Map<Integer, String> classNames = detection.classNames();

        List<Box> boxes = new ArrayList<>();
        for (List<Float> bx : bs) {
            Box box = new Box(bx.get(0), bx.get(1), bx.get(2), bx.get(3), bx.get(4), bx.get(5), classNames);
            boxes.add(box);
        }

        return boxes;
    }

    public List<Box> sam(DetectionRequest imgAreaInput) throws IOException, OrtException {
        Mat mat = ImageUtil.urlToMat(imgAreaInput.getImgUrl());
        return YoloFastSAMUtil.predictor(mat);
    }

    public BufferedImage samI(DetectionRequest imgAreaInput) throws IOException, OrtException {
        BufferedImage image = ImageUtil.urlToImage(imgAreaInput.getImgUrl());


        List<Box> boxs = this.sam(imgAreaInput);

        ImageUtil.drawImageWithBox(image, boxs);
        return image;
    }

    public List<SegDetection> segArea(DetectionRequestWithArea imgAreaInput) throws IOException, OrtException {

        return YoloV11SegUtil.predictor(ImageUtil.urlToMat(imgAreaInput.getImgUrl()), imgAreaInput.getThreshold());
    }

    public BufferedImage segAreaI(DetectionRequestWithArea imgAreaInput) throws IOException, OrtException {
        List<SegDetection> segs = segArea(imgAreaInput);
        BufferedImage img = ImageUtil.urlToImage(imgAreaInput.getImgUrl());
        List<List<Point>> pointList = new ArrayList<>();
        for (SegDetection seg : segs) {
            pointList.addAll(seg.getPoints());
        }
        ImageUtil.drawImageWithListPoint(img, pointList);
        return img;
    }
}
