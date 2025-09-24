package com.yuqiangdede.ffe.core.base;

import java.util.Map;

import com.yuqiangdede.ffe.core.domain.FaceInfo;
import com.yuqiangdede.ffe.core.domain.ImageMat;

/**
 * 人脸关键点检测
 */
public interface FaceKeyPoint {

    /**
     * 人脸关键点检测
     * @param imageMat  图像数据
     * @param params    参数信息
     * @return
     */
    FaceInfo.Points inference(ImageMat imageMat, Map<String, Object> params);

}
