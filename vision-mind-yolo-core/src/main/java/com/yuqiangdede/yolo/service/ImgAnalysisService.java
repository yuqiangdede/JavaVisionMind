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


/**
 * 1、传入url分析
 * 2、传入base64分析
 */
@Service
@Slf4j
public class ImgAnalysisService {

    static {
        // 加载opencv需要的动态库
        String osName = System.getProperty("os.name").toLowerCase();
        if (osName.contains("win")) {
            System.load(Constant.OPENCV_DLL_PATH);
        } else if (osName.contains("nix") || osName.contains("nux") || osName.contains("aix")) {
            System.load(Constant.OPENCV_SO_PATH);
        } else {
            throw new UnsupportedOperationException("Unsupported operating system: " + osName);
        }
    }


    /**
     * 检测给定图片中的特定区域，返回包含该区域内目标物体的Box对象列表
     *
     * @param imgAreaInput 图片URL，坐标等
     * @return 包含该区域内目标物体的Box对象列表
     * @throws Exception 如果坐标范围无效或图片加载失败，则抛出异常
     */
    public List<Box> detectArea(DetectionRequestWithArea imgAreaInput) throws Exception {
        // 将图片URL转换为Mat对象
        Mat mat = ImageUtil.urlToMat(imgAreaInput.getImgUrl());

        // 先进行全图检测
        List<Box> boxs = analysis(mat, imgAreaInput.getThreshold(), imgAreaInput.getTypes());
        // 待返回的集合
        Set<Box> result = new LinkedHashSet<>();

        if (imgAreaInput.getDetectionFrames() == null || imgAreaInput.getDetectionFrames().isEmpty()) {
            // 如果检测框为空，就说明要全图检测，把所有的目标框放进去
            result.addAll(boxs);
        } else {
            // 有检测框的情况下，就遍历检测框和目标框的重叠百分比
            for (Box box : boxs) {
                for (ArrayList<Point> detectionFrame : imgAreaInput.getDetectionFrames()) {
                    double detectRatio = GeometryUtils.calcOverlap(box, detectionFrame);
                    // 如果目标框和检测框的重叠比例大于给定的值 就放入待返回的数据
                    if (detectRatio > Constant.DETECT_RATIO) {
                        result.add(box);
                        break;
                    }
                }
            }
        }


        if (imgAreaInput.getBlockingFrames() == null || imgAreaInput.getBlockingFrames().isEmpty()) {
            // 如果屏蔽框为空，就说明不需要进行过滤，直接返回
            return new ArrayList<>(result);
        } else {
            // 如果屏蔽框为非空，创建一个要移除的box集合以避免在遍历过程中修改result集合
            Set<Box> toRemove = new HashSet<>();
            for (Box box : boxs) {
                for (ArrayList<Point> blockingFrame : imgAreaInput.getBlockingFrames()) {
                    double blockRatio = GeometryUtils.calcOverlap(box, blockingFrame);
                    // 如果目标框和屏蔽框的重叠比例大于给定的值 就移除待返回的数据
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

    public BufferedImage detectAreaI(DetectionRequestWithArea imgAreaInput) throws Exception {
        BufferedImage image = ImageUtil.urlToImage(imgAreaInput.getImgUrl());

        List<Box> boxs = this.detectArea(imgAreaInput);

        // 绘制目标框和文字
        ImageUtil.drawImageWithBox(image, boxs);
        // 蓝色的检测框 灰的是屏蔽框
        ImageUtil.drawImageWithFrames(image, imgAreaInput.getDetectionFrames(), Color.BLUE);
        ImageUtil.drawImageWithFrames(image, imgAreaInput.getBlockingFrames(), Color.DARK_GRAY);
        return image;
    }

    /**
     * 使用ONNX模型对输入的Mat对象进行预测，并返回预测结果的边界框信息列表。
     *
     * @param mat 待预测的Mat对象
     * @return 包含预测结果的边界框信息列表
     */
    private List<Box> analysis(Mat mat, Float conf, String types) {
        // 使用ONNX模型进行预测
        YoloDetectionResult detection = YoloV11Util.predictor(mat, conf);
        List<List<Float>> bs = detection.boxes();
        Map<Integer, String> classNames = detection.classNames();

        List<Box> boxes = new ArrayList<>();
        for (List<Float> bx : bs) {
            // 根据边界框信息和类别名称映射创建Box对象
            Box box = new Box(bx.get(0), bx.get(1), bx.get(2), bx.get(3), bx.get(4), bx.get(5), classNames);
            if (types == null || types.isEmpty()) {
                // 没有传入类型数据，使用配置文件默认的数据
                if (Constant.YOLO_TYPES.contains(box.getType())) {
                    // 如果检测到目标，则添加到结果列表中
                    boxes.add(box);
                }
            } else {
                List<Integer> typeList = Arrays.stream(types.split(","))
                        .map(Integer::parseInt)
                        .collect(Collectors.toList());

                if (typeList.contains(box.getType())) {
                    // 如果检测到目标，则添加到结果列表中
                    boxes.add(box);
                }
            }
        }

        // 返回结果列表
        return boxes;
    }


    /**
     * 姿态检测
     *
     * @param imgAreaInput 图片的URL地址 置信度 类型
     * @return 包含检测结果的Box对象列表
     * @throws Exception 如果在检测过程中发生异常，则抛出该异常
     */
    public List<BoxWithKeypoints> poseArea(DetectionRequestWithArea imgAreaInput) throws Exception {
        // 将图片URL转换为Mat对象
        Mat mat = ImageUtil.urlToMat(imgAreaInput.getImgUrl());

        // 先进行全图检测
        List<BoxWithKeypoints> boxs = analysisPose(mat, imgAreaInput.getThreshold());
        // 待返回的集合
        Set<BoxWithKeypoints> result = new LinkedHashSet<>();

        if (imgAreaInput.getDetectionFrames() == null || imgAreaInput.getDetectionFrames().isEmpty()) {
            // 如果检测框为空，就说明要全图检测，把所有的目标框放进去
            result.addAll(boxs);
        } else {
            // 有检测框的情况下，就遍历检测框和目标框的重叠百分比
            for (BoxWithKeypoints box : boxs) {
                for (ArrayList<Point> detectionFrame : imgAreaInput.getDetectionFrames()) {
                    double detectRatio = GeometryUtils.calcOverlap(box, detectionFrame);
                    // 如果目标框和检测框的重叠比例大于给定的值 就放入待返回的数据
                    if (detectRatio > Constant.DETECT_RATIO) {
                        result.add(box);
                        break;
                    }
                }
            }
        }

        if (imgAreaInput.getBlockingFrames() == null || imgAreaInput.getBlockingFrames().isEmpty()) {
            // 如果屏蔽框为空，就说明不需要进行过滤，直接返回
            return new ArrayList<>(result);
        } else {
            // 如果屏蔽框为非空，创建一个要移除的box集合以避免在遍历过程中修改result集合
            Set<BoxWithKeypoints> toRemove = new HashSet<>();
            for (BoxWithKeypoints box : boxs) {
                for (ArrayList<Point> blockingFrame : imgAreaInput.getBlockingFrames()) {
                    double blockRatio = GeometryUtils.calcOverlap(box, blockingFrame);
                    // 如果目标框和屏蔽框的重叠比例大于给定的值 就移除待返回的数据
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

    public BufferedImage poseAreaI(DetectionRequestWithArea imgAreaInput) throws Exception {
        BufferedImage image = ImageUtil.urlToImage(imgAreaInput.getImgUrl());

        List<BoxWithKeypoints> boxs = this.poseArea(imgAreaInput);

        ImageUtil.drawImageWithKeypoints(image, boxs);
        // 蓝色的检测框 灰的是屏蔽框
        ImageUtil.drawImageWithFrames(image, imgAreaInput.getDetectionFrames(), Color.BLUE);
        ImageUtil.drawImageWithFrames(image, imgAreaInput.getBlockingFrames(), Color.DARK_GRAY);
        return image;
    }

    private List<BoxWithKeypoints> analysisPose(Mat mat, Float conf) {
        // 使用ONNX模型进行预测
        YoloPoseDetectionResult detection = YoloV11PoseUtil.predictor(mat, conf);
        List<List<Float>> bs = detection.boxes();

        List<BoxWithKeypoints> boxes = new ArrayList<>();
        for (List<Float> bx : bs) {
            // 根据边界框信息和类别名称映射创建Box对象
            BoxWithKeypoints box = new BoxWithKeypoints(bx.get(0), bx.get(1), bx.get(2), bx.get(3), bx.get(4));
            // 补充关键点数据
            box.injectKeypoints(bx.subList(5, 56), mat.height(), mat.width());

            boxes.add(box);
        }

        // 返回结果列表
        return boxes;
    }

    public List<Box> detectFace(DetectionRequestWithArea imgAreaInput) throws Exception {
        // 将图片URL转换为Mat对象
        Mat mat = ImageUtil.urlToMat(imgAreaInput.getImgUrl());

        // 先进行全图检测
        List<Box> boxs = analysisFace(mat, imgAreaInput.getThreshold());
        // 待返回的集合
        Set<Box> result = new LinkedHashSet<>();

        if (imgAreaInput.getDetectionFrames() == null || imgAreaInput.getDetectionFrames().isEmpty()) {
            // 如果检测框为空，就说明要全图检测，把所有的目标框放进去
            result.addAll(boxs);
        } else {
            // 有检测框的情况下，就遍历检测框和目标框的重叠百分比
            for (Box box : boxs) {
                for (ArrayList<Point> detectionFrame : imgAreaInput.getDetectionFrames()) {
                    double detectRatio = GeometryUtils.calcOverlap(box, detectionFrame);
                    // 如果目标框和检测框的重叠比例大于给定的值 就放入待返回的数据
                    if (detectRatio > Constant.DETECT_RATIO) {
                        result.add(box);
                        break;
                    }
                }
            }
        }


        if (imgAreaInput.getBlockingFrames() == null || imgAreaInput.getBlockingFrames().isEmpty()) {
            // 如果屏蔽框为空，就说明不需要进行过滤，直接返回
            return new ArrayList<>(result);
        } else {
            // 如果屏蔽框为非空，创建一个要移除的box集合以避免在遍历过程中修改result集合
            Set<Box> toRemove = new HashSet<>();
            for (Box box : boxs) {
                for (ArrayList<Point> blockingFrame : imgAreaInput.getBlockingFrames()) {
                    double blockRatio = GeometryUtils.calcOverlap(box, blockingFrame);
                    // 如果目标框和屏蔽框的重叠比例大于给定的值 就移除待返回的数据
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

    public BufferedImage detectFaceI(DetectionRequestWithArea imgAreaInput) throws Exception {
        BufferedImage image = ImageUtil.urlToImage(imgAreaInput.getImgUrl());

        List<Box> boxs = this.detectFace(imgAreaInput);

        ImageUtil.drawImageWithBox(image, boxs);
        // 蓝色的检测框 灰的是屏蔽框
        ImageUtil.drawImageWithFrames(image, imgAreaInput.getDetectionFrames(), Color.BLUE);
        ImageUtil.drawImageWithFrames(image, imgAreaInput.getBlockingFrames(), Color.DARK_GRAY);
        return image;
    }

    private List<Box> analysisFace(Mat mat, Float conf) {
        // 使用ONNX模型进行预测
        YoloDetectionResult detection = YoloV11Util.predictorFace(mat, conf);

        List<List<Float>> bs = detection.boxes();
        Map<Integer, String> classNames = detection.classNames();

        List<Box> boxes = new ArrayList<>();
        for (List<Float> bx : bs) {
            // 根据边界框信息和类别名称映射创建Box对象
            Box box = new Box(bx.get(0), bx.get(1), bx.get(2), bx.get(3), bx.get(4), bx.get(5), classNames);
            boxes.add(box);
        }

        // 返回结果列表
        return boxes;
    }

    public List<Box> sam(DetectionRequest imgAreaInput) throws Exception {
        Mat mat = ImageUtil.urlToMat(imgAreaInput.getImgUrl());
        return YoloFastSAMUtil.predictor(mat);
    }

    public BufferedImage samI(DetectionRequest imgAreaInput) throws Exception {
        // 拿到原图
        BufferedImage image = ImageUtil.urlToImage(imgAreaInput.getImgUrl());


        // 进行sam检测，拿到坐标框
        List<Box> boxs = this.sam(imgAreaInput);
//
//        Mat mat = ImageUtil.urlToMat(imgAreaInput.getImgUrl());
//        for (Box b : boxs) {
//            Imgproc.rectangle(mat,
//                    new org.opencv.core.Point(b.getX1(), b.getY1()),
//                    new org.opencv.core.Point(b.getX2(), b.getY2()),
//                    new Scalar(0, 255, 0),
//                    2,
//                    Imgproc.LINE_8,
//                    0
//            );
//        }
//        Imgcodecs.imwrite("debug640.jpg", mat);

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
