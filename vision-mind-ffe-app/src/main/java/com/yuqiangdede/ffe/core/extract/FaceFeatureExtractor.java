package com.yuqiangdede.ffe.core.extract;

import com.yuqiangdede.ffe.core.domain.FaceImage;
import com.yuqiangdede.ffe.core.domain.ImageMat;

import java.util.Map;

/**
 * 人脸特征提取器
 */
public interface FaceFeatureExtractor {

    /**
     * 人脸特征提取
     * @param image
     * @param params
     * @return
     */
    public FaceImage extract(ImageMat image, Map<String, Object> params);

}
