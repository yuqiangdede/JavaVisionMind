package com.yuqiangdede.yolo.dto.input;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DetectionRequest {

    /**
     * 图片url
     */
    private String imgUrl;
    /**
     * 置信度阈值，可选，不填就使用配置文件yolo.conf.Threshold 或者 yolo.pose.conf.Threshold 的值
     */
    private Float threshold;
    /**
     * 目标类型，可选，不填就使用配置文件yolo.types的值
     */
    private String types;



    @Override
    public String toString() {
        return "DetectionRequest{" +
                "imgUrl='" + imgUrl + '\'' +
                ", threshold=" + threshold +
                ", types='" + types + '\'' +
                '}';
    }
}
