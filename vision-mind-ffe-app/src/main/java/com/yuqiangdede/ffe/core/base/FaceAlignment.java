package com.yuqiangdede.ffe.core.base;

import java.util.Map;

import com.yuqiangdede.ffe.core.domain.FaceInfo;
import com.yuqiangdede.ffe.core.domain.ImageMat;

/**
 * 对图像进行对齐
 */
public interface FaceAlignment {

    /**
     * 对图像进行对齐
     * @param imageMat  图像信息
     * @imagePoint
     * @param params    参数信息
     * @return
     */
    ImageMat inference(ImageMat imageMat, FaceInfo.Points imagePoint, Map<String, Object> params);

}
