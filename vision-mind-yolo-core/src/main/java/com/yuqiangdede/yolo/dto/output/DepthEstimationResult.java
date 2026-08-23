package com.yuqiangdede.yolo.dto.output;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * YOLO26 单目深度估计结果。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DepthEstimationResult {

    private int width;
    private int height;
    private String unit;
    private String layout;
    private long validPixelCount;
    private float minDepth;
    private float maxDepth;
    private float meanDepth;
    private float medianDepth;

    /**
     * 按行优先排列的米制深度图，索引计算方式为 y * width + x。
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private float[] depthMap;
}
