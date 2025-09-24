package com.yuqiangdede.tbir.dto.output;

import com.yuqiangdede.common.dto.output.Box;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class HitImage {

    /**
     * 匹配图像的 ID
     */
    private String imageId;

    /**
     * 匹配到的子图框坐标
     */
    private List<Box> matchedBoxes;

    /**
     * 相似度得分（一般范围 0~1）
     */
    private double score;

    /**
     * 主图的 URL 地址（如果支持图片预览）
     */
    private String imageUrl;
    private String cameraId;               // 监控点 ID
    private String groupId;                // 分组 ID
    private Map<String, String> meta;      // 主图的元信息（事件时间、来源等）
}
