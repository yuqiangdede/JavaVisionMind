package com.yuqiangdede.yolo.dto.input;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 单目深度估计请求。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DepthEstimationRequest {

    /**
     * 图片 URL、本地路径、file URI 或 data URI。
     */
    private String imgUrl;

    /**
     * 预览模式：disparity（默认，近处为暖色）或 metric（按米线性着色）。
     */
    private String visualizationMode;

    /**
     * metric 模式的最小深度，单位米。
     */
    private Float minDepth;

    /**
     * metric 模式的最大深度，单位米。
     */
    private Float maxDepth;

    /**
     * 是否在 JSON 响应中携带完整深度数组。默认不携带，生产调用建议使用 /depth/map 二进制接口。
     */
    private Boolean includeDepthMap;
}
