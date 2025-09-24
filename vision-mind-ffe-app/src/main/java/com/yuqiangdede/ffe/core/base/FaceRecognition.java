package com.yuqiangdede.ffe.core.base;

import com.yuqiangdede.ffe.core.domain.FaceInfo;
import com.yuqiangdede.ffe.core.domain.ImageMat;

import java.util.Map;

/**
 * 人脸识别模型
 */
public interface FaceRecognition {

    /**
     * 人脸识别，人脸特征向量
     * @param image 图像信息
     * @param params    参数信息
     * @return
     */
    FaceInfo.Embedding inference(ImageMat image, Map<String, Object> params);

}
