package com.yuqiangdede.ffe.core.models;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import com.yuqiangdede.ffe.core.base.BaseOnnxInfer;
import com.yuqiangdede.ffe.core.base.FaceDetection;
import com.yuqiangdede.ffe.core.domain.FaceInfo;
import com.yuqiangdede.ffe.core.domain.ImageMat;
import com.yuqiangdede.ffe.core.utils.ReleaseUtil;
import org.opencv.core.Core;
import org.opencv.core.Scalar;

import java.util.*;

/**
 * 人脸识别-SCRFD
 * git:<a href="https://github.com/deepinsight/insightface/tree/master/detection/scrfd">...</a>
 */
public class InsightScrfdFaceDetection extends BaseOnnxInfer implements FaceDetection {
    //图像的最大尺寸
    private final static int maxSizeLength = 640;
    //模型人脸检测的步长
    private final static int[] strides = new int[]{8, 16, 32};
    //人脸预测分数阈值
    public final static float defScoreTh = 0.5f;
    //人脸重叠iou阈值
    public final static float defIouTh = 0.7f;
    //给人脸框一个默认的缩放
    public final static float defBoxScale = 1.0f;
    //人脸框缩放参数KEY
    public final static String scrfdFaceboxScaleParamKey = "scrfdFaceboxScale";
    //人脸框默认需要进行角度检测
    public final static boolean defNeedCheckFaceAngle = true;
    //是否需要进行角度检测的参数KEY
    public final static String scrfdFaceNeedCheckFaceAngleParamKey = "scrfdFaceNeedCheckFaceAngle";
    //人脸框默认需要进行角度检测
    public final static boolean defNoFaceImageNeedMakeBorder = true;
    //是否需要进行角度检测的参数KEY
    public final static String scrfdNoFaceImageNeedMakeBorderParamKey = "scrfdNoFaceImageNeedMakeBorder";

    /**
     * 构造函数
     *
     * @param modelPath 模型路径
     * @param threads   线程数
     */
    public InsightScrfdFaceDetection(String modelPath, int threads) {
        super(modelPath, threads);
    }

    /**
     * 获取人脸信息
     *
     * @param image   图像信息
     * @param scoreTh 人脸人数阈值
     * @param iouTh   人脸iou阈值
     * @return 人脸模型
     */
    @Override
    public List<FaceInfo> inference(ImageMat image, float scoreTh, float iouTh, Map<String, Object> params) {
        List<FaceInfo> faceInfos = this.modelInference(image, scoreTh, iouTh, params);
        //对图像进行补边操作，进行二次识别
        if (this.getNoFaceImageNeedMakeBorder(params) && faceInfos.isEmpty()) {
            //防止由于人脸占用大，导致检测模型识别失败
            int t = Double.valueOf(image.toCvMat().height() * 0.2).intValue();
            int b = Double.valueOf(image.toCvMat().height() * 0.2).intValue();
            int l = Double.valueOf(image.toCvMat().width() * 0.2).intValue();
            int r = Double.valueOf(image.toCvMat().width() * 0.2).intValue();
            ImageMat tempMat = null;
            try {
                //补边识别
                tempMat = image.copyMakeBorderAndNotReleaseMat(t, b, l, r, Core.BORDER_CONSTANT);
                faceInfos = this.modelInference(tempMat, scoreTh, iouTh, params);
                for (FaceInfo faceInfo : faceInfos) {
                    //还原原始的坐标
                    faceInfo.box = faceInfo.box.move(l, 0, t, 0);
                    faceInfo.points = faceInfo.points.move(l, 0, t, 0);
                }
            } finally {
                ReleaseUtil.release(tempMat);
            }
        }
        return faceInfos;
    }


    /**
     * 模型推理，获取人脸信息
     *
     * @param image   图像信息
     * @param scoreTh 人脸人数阈值
     * @param iouTh   人脸iou阈值
     * @return 人脸模型
     */
    public List<FaceInfo> modelInference(ImageMat image, float scoreTh, float iouTh, Map<String, Object> params) {
        OnnxTensor tensor = null;
        OrtSession.Result output = null;
        ImageMat imageMat = image.clone();
        try {
            // 设置图像缩放比例
            float imgScale = 1.0f;
            // 获取人脸框缩放比例
            float boxScale = getBoxScale(params);
            // 设置iou阈值
            iouTh = iouTh <= 0 ? defIouTh : iouTh;
            // 设置人脸人数阈值
            scoreTh = scoreTh <= 0 ? defScoreTh : scoreTh;

            int imageWidth = imageMat.getWidth(), imageHeight = imageMat.getHeight();
            int modelWidth = imageWidth, modelHeight = imageHeight;
            // 如果图像宽度或高度超过最大长度，则进行缩放
            if (imageWidth > maxSizeLength || imageHeight > maxSizeLength) {
                if (imageWidth > imageHeight) {
                    modelWidth = maxSizeLength;
                    imgScale = 1.0f * imageWidth / maxSizeLength;
                    modelHeight = imageHeight * maxSizeLength / imageWidth;
                } else {
                    modelHeight = maxSizeLength;
                    imgScale = 1.0f * imageHeight / maxSizeLength;
                    modelWidth = modelWidth * maxSizeLength / imageHeight;
                }
                imageMat = imageMat.resizeAndDoReleaseMat(modelWidth, modelHeight);
            }

            // 将图像转换为OnnxTensor
            tensor = imageMat
                    .blobFromImageAndDoReleaseMat(1.0 / 128, new Scalar(127.5, 127.5, 127.5), true)
                    .to4dFloatOnnxTensorAndDoReleaseMat(true);

            // 运行模型推理
            output = getSession().run(Collections.singletonMap(getInputName(), tensor));

            // 获取人脸信息
            List<FaceInfo> faceInfos = fitterBoxes(output, scoreTh, iouTh, tensor.getInfo().getShape()[3], imgScale, boxScale);

            // 对人脸进行角度检查
            return this.checkFaceAngle(faceInfos, this.getNeedCheckFaceAngle(params));
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            // 关闭OnnxTensor
            if (null != tensor) {
                tensor.close();
            }
            // 关闭OrtSession.Result
            if (null != output) {
                output.close();
            }
            // 释放ImageMat资源
            if (null != imageMat) {
                imageMat.release();
            }
        }
    }


    /**
     * 过滤人脸框
     *
     * @param output      数据输出
     * @param scoreTh     人脸分数阈值
     * @param iouTh       人脸重叠阈值
     * @param tensorWidth 输出层的宽度
     * @param imgScale    图像的缩放比例
     */
    private List<FaceInfo> fitterBoxes(OrtSession.Result output, float scoreTh, float iouTh, long tensorWidth, float imgScale, float boxScale) throws OrtException {
        //分数过滤及计算正确的人脸框值
        List<FaceInfo> faceInfos = new ArrayList<>();
        for (int index = 0; index < 3; index++) {
            float[][] scores = (float[][]) output.get(index).getValue();
            float[][] boxes = (float[][]) output.get(index + 3).getValue();
            float[][] points = (float[][]) output.get(index + 6).getValue();
            int ws = (int) Math.ceil(1.0f * tensorWidth / strides[index]);
            for (int i = 0; i < scores.length; i++) {
                if (scores[i][0] >= scoreTh) {
                    int anchorIndex = i / 2;
                    int rowNum = anchorIndex / ws;
                    int colNum = anchorIndex % ws;
                    //计算人脸框
                    float anchorX = colNum * strides[index];
                    float anchorY = rowNum * strides[index];
                    float x1 = (anchorX - boxes[i][0] * strides[index]) * imgScale;
                    float y1 = (anchorY - boxes[i][1] * strides[index]) * imgScale;
                    float x2 = (anchorX + boxes[i][2] * strides[index]) * imgScale;
                    float y2 = (anchorY + boxes[i][3] * strides[index]) * imgScale;
                    //计算关键点
                    float[] point = points[i];
                    FaceInfo.Points keyPoints = FaceInfo.Points.build();
                    for (int pointIndex = 0; pointIndex < (point.length / 2); pointIndex++) {
                        float pointX = (point[2 * pointIndex] * strides[index] + anchorX) * imgScale;
                        float pointY = (point[2 * pointIndex + 1] * strides[index] + anchorY) * imgScale;
                        keyPoints.add(FaceInfo.Point.build(pointX, pointY));
                    }
                    faceInfos.add(FaceInfo.build(scores[i][0], 0, FaceInfo.FaceBox.build(x1, y1, x2, y2).scaling(boxScale), keyPoints));
                }
            }
        }
        //对人脸框进行iou过滤
        Collections.sort(faceInfos);
        List<FaceInfo> faces = new ArrayList<>();
        while (!faceInfos.isEmpty()) {
            Iterator<FaceInfo> iterator = faceInfos.iterator();
            //获取第一个元素，并删除元素
            FaceInfo firstFace = iterator.next();
            iterator.remove();
            //对比后面元素与第一个元素之间的iou
            while (iterator.hasNext()) {
                FaceInfo nextFace = iterator.next();
                if (firstFace.iou(nextFace) >= iouTh) {
                    iterator.remove();
                }
            }
            faces.add(firstFace);
        }
        //返回
        return faces;
    }

    /**
     * 对人脸进行角度检测，这里通过5个关键点来确定当前人脸的角度
     *
     * @param faceInfos          人脸信息
     * @param needCheckFaceAngle 是否启用检测
     */
    private List<FaceInfo> checkFaceAngle(List<FaceInfo> faceInfos, boolean needCheckFaceAngle) {
        if (!needCheckFaceAngle || null == faceInfos || faceInfos.isEmpty()) {
            return faceInfos;
        }
        for (FaceInfo faceInfo : faceInfos) {
            //计算当前人脸的角度数据
            float ax1 = faceInfo.points.get(1).x;
            float ay1 = faceInfo.points.get(1).y;
            float ax2 = faceInfo.points.get(0).x;
            float ay2 = faceInfo.points.get(0).y;
            int atan = Double.valueOf(Math.atan2((ay2 - ay1), (ax2 - ax1)) / Math.PI * 180).intValue();
            int angle = (180 - atan + 360) % 360;
            int ki = (angle + 45) % 360 / 90;
            int rotate = angle - (90 * ki); //
            float scaling = 1 + Double.valueOf(Math.abs(Math.sin(Math.toRadians(rotate)))).floatValue() / 3;
            faceInfo.angle = angle;
            //重组坐标点, 旋转及缩放
            if (ki == 0) {
                FaceInfo.Point leftTop = FaceInfo.Point.build(faceInfo.box.x1(), faceInfo.box.y1());
                FaceInfo.Point rightTop = FaceInfo.Point.build(faceInfo.box.x2(), faceInfo.box.y1());
                FaceInfo.Point rightBottom = FaceInfo.Point.build(faceInfo.box.x2(), faceInfo.box.y2());
                FaceInfo.Point leftBottom = FaceInfo.Point.build(faceInfo.box.x1(), faceInfo.box.y2());
                faceInfo.box = new FaceInfo.FaceBox(leftTop, rightTop, rightBottom, leftBottom);
                faceInfo.box = faceInfo.box.rotate(rotate).scaling(scaling).rotate(-angle);
            } else if (ki == 1) {
                FaceInfo.Point leftTop = FaceInfo.Point.build(faceInfo.box.x1(), faceInfo.box.y2());
                FaceInfo.Point rightTop = FaceInfo.Point.build(faceInfo.box.x1(), faceInfo.box.y1());
                FaceInfo.Point rightBottom = FaceInfo.Point.build(faceInfo.box.x2(), faceInfo.box.y1());
                FaceInfo.Point leftBottom = FaceInfo.Point.build(faceInfo.box.x2(), faceInfo.box.y2());
                faceInfo.box = new FaceInfo.FaceBox(leftTop, rightTop, rightBottom, leftBottom);
                faceInfo.box = faceInfo.box.rotate(rotate).scaling(scaling).rotate(-angle);
            } else if (ki == 2) {
                FaceInfo.Point leftTop = FaceInfo.Point.build(faceInfo.box.x2(), faceInfo.box.y2());
                FaceInfo.Point rightTop = FaceInfo.Point.build(faceInfo.box.x1(), faceInfo.box.y2());
                FaceInfo.Point rightBottom = FaceInfo.Point.build(faceInfo.box.x1(), faceInfo.box.y1());
                FaceInfo.Point leftBottom = FaceInfo.Point.build(faceInfo.box.x2(), faceInfo.box.y1());
                faceInfo.box = new FaceInfo.FaceBox(leftTop, rightTop, rightBottom, leftBottom);
                faceInfo.box = faceInfo.box.rotate(rotate).scaling(scaling).rotate(-angle);
            } else if (ki == 3) {
                FaceInfo.Point leftTop = FaceInfo.Point.build(faceInfo.box.x2(), faceInfo.box.y1());
                FaceInfo.Point rightTop = FaceInfo.Point.build(faceInfo.box.x2(), faceInfo.box.y2());
                FaceInfo.Point rightBottom = FaceInfo.Point.build(faceInfo.box.x1(), faceInfo.box.y2());
                FaceInfo.Point leftBottom = FaceInfo.Point.build(faceInfo.box.x1(), faceInfo.box.y1());
                faceInfo.box = new FaceInfo.FaceBox(leftTop, rightTop, rightBottom, leftBottom);
                faceInfo.box = faceInfo.box.rotate(rotate).scaling(scaling).rotate(-angle);
            }
        }
        return faceInfos;
    }

    /**
     * 人脸框的默认缩放比例
     **/
    private float getBoxScale(Map<String, Object> params) {
        float boxScale = 0;
        try {
            if (null != params && params.containsKey(scrfdFaceboxScaleParamKey)) {
                Object value = params.get(scrfdFaceboxScaleParamKey);
                if (null != value) {
                    if (value instanceof Number) {
                        boxScale = ((Number) value).floatValue();
                    } else {
                        boxScale = Float.parseFloat(value.toString());
                    }
                }
            }
        } catch (Exception e) {
        }
        return boxScale > 0 ? boxScale : defBoxScale;
    }

    /**
     * 获取是否需要进行角度探测
     **/
    private boolean getNeedCheckFaceAngle(Map<String, Object> params) {
        boolean needCheckFaceAngle = defNeedCheckFaceAngle;
        try {
            if (null != params && params.containsKey(scrfdFaceNeedCheckFaceAngleParamKey)) {
                Object value = params.get(scrfdFaceNeedCheckFaceAngleParamKey);
                if (null != value) {
                    if (value instanceof Boolean) {
                        needCheckFaceAngle = (boolean) value;
                    } else {
                        needCheckFaceAngle = Boolean.parseBoolean(value.toString());
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return needCheckFaceAngle;
    }

    /**
     * 获取是否需要对没有检测到人脸的图像进行补边二次识别
     **/
    private boolean getNoFaceImageNeedMakeBorder(Map<String, Object> params) {
        boolean noFaceImageNeedMakeBorder = defNoFaceImageNeedMakeBorder;
        try {
            if (null != params && params.containsKey(scrfdNoFaceImageNeedMakeBorderParamKey)) {
                Object value = params.get(scrfdNoFaceImageNeedMakeBorderParamKey);
                if (null != value) {
                    if (value instanceof Boolean) {
                        noFaceImageNeedMakeBorder = (boolean) value;
                    } else {
                        noFaceImageNeedMakeBorder = Boolean.parseBoolean(value.toString());
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return noFaceImageNeedMakeBorder;
    }
}
