package com.yuqiangdede.ffe.core.extract;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.yuqiangdede.ffe.core.base.*;
import com.yuqiangdede.ffe.core.domain.ExtParam;
import com.yuqiangdede.ffe.core.domain.FaceImage;
import com.yuqiangdede.ffe.core.domain.FaceInfo;
import com.yuqiangdede.ffe.core.domain.ImageMat;
import com.yuqiangdede.ffe.core.models.Simple106pFaceAlignment;
import com.yuqiangdede.ffe.core.utils.CropUtil;
import com.yuqiangdede.ffe.core.utils.MaskUtil;
import com.yuqiangdede.ffe.core.models.InsightCoordFaceKeyPoint;

/**
 * 人脸特征提取器实现
 */
public class FaceFeatureExtractorImpl implements FaceFeatureExtractor {

    public static final float defScaling = 1.5f;
    ExtParam extParam = ExtParam.build().setMask(true).setTopK(20).setScoreTh(0).setIouTh(0);

    private final FaceKeyPoint faceKeyPoint;
    private final FaceDetection faceDetection;
    private final FaceAlignment faceAlignment;
    private final FaceRecognition faceRecognition;
    private final FaceAttribute faceAttribute;

    /**
     * 构造函数
     * @param faceDetection         人脸识别模型
     * @param faceKeyPoint          人脸关键点模型
     * @param faceAlignment         人脸对齐模型
     * @param faceRecognition       人脸特征提取模型
     */
    public FaceFeatureExtractorImpl(
            FaceDetection faceDetection,
            FaceKeyPoint faceKeyPoint, FaceAlignment faceAlignment,
            FaceRecognition faceRecognition, FaceAttribute faceAttribute) {
        this.faceKeyPoint = faceKeyPoint;
        this.faceDetection = faceDetection;
        this.faceAlignment = faceAlignment;
        this.faceAttribute = faceAttribute;
        this.faceRecognition = faceRecognition;
    }

    /**
     * 人脸特征提取
     * @param image
     * @param params
     * @return
     */
    @Override
    public FaceImage extract(ImageMat image, Map<String, Object> params) {
        //人脸识别
        List<FaceInfo> faceInfos =  this.faceDetection.inference(image, extParam.getScoreTh(), extParam.getIouTh(), params);

        //取人脸topK
        int topK = (extParam.getTopK()  > 0) ? extParam.getTopK() : 5;
        if(faceInfos.size() > topK){
            faceInfos = faceInfos.subList(0, topK);
        }
        //处理数据
        for(FaceInfo faceInfo : faceInfos) {
            ImageMat cropImageMat = null;
            ImageMat alignmentImage = null;
            try {
                //通过旋转角度获取正脸坐标，并进行图像裁剪
                FaceInfo.FaceBox rotateFaceBox = faceInfo.rotateFaceBox();
                cropImageMat = ImageMat.fromCVMat(CropUtil.crop(image.toCvMat(), rotateFaceBox));
                //人脸属性检测
                FaceInfo.Attribute attribute = this.faceAttribute.inference(cropImageMat, params);
                faceInfo.attribute = attribute;
                cropImageMat.release();
                cropImageMat = null;
                //进行缩放人脸区域，并裁剪图片
                float scaling = extParam.getScaling() <= 0 ? defScaling : extParam.getScaling();
                FaceInfo.FaceBox box = rotateFaceBox.scaling(scaling);
                cropImageMat = ImageMat.fromCVMat(CropUtil.crop(image.toCvMat(), box));
                //人脸标记关键点
                FaceInfo.Points corpPoints = this.faceKeyPoint.inference(cropImageMat, params);
                //还原原始图片中的关键点
                FaceInfo.Point corpImageCenter = FaceInfo.Point.build((float)cropImageMat.center().x, (float)cropImageMat.center().y);
                FaceInfo.Points imagePoints = corpPoints.rotation(corpImageCenter, faceInfo.angle).operateSubtract(corpImageCenter);
                faceInfo.points = imagePoints.operateAdd(box.center());
                //人脸对齐
                alignmentImage = this.faceAlignment.inference(cropImageMat, corpPoints, params);
                //判断是否需要遮罩人脸以外的区域
                if(extParam.isMask()){
                    if(faceKeyPoint instanceof InsightCoordFaceKeyPoint && faceAlignment instanceof Simple106pFaceAlignment){
                        alignmentImage = MaskUtil.maskFor106InsightCoordModel(
                                alignmentImage,
                                Simple106pFaceAlignment.templatePoints(),
                                true);
                    }
                }
                //人脸特征提取
                FaceInfo.Embedding embedding = this.faceRecognition.inference(alignmentImage, params);
                faceInfo.embedding = embedding;
                //设置人脸ID
                faceInfo.setId(UUID.randomUUID().toString());
            }finally {
                if(null != alignmentImage){
                    alignmentImage.release();
                }
                if(null != cropImageMat){
                    cropImageMat.release();
                }
            }

        }
        return FaceImage.build(image.toBase64AndNoReleaseMat(), faceInfos);
    }

}
