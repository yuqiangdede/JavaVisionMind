package com.yuqiangdede.yolo.dto.input;

import com.yuqiangdede.common.dto.Point;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TextPromptRequestWithArea {

    /**
     * 图片url
     */
    private String imgUrl;
    /**
     * 置信度阈值，可选，不填就使用配置文件yolo.conf.Threshold的值
     */
    private Float threshold;
    /**
     * 检测框 原始坐标
     */
    public ArrayList<ArrayList<Point>> detectionFrames;
    /**
     * 屏蔽框 原始坐标
     */
    public ArrayList<ArrayList<Point>> blockingFrames;

    @Override
    public String toString() {
        return "TextPromptRequestWithArea{" +
                "imgUrl='" + imgUrl + '\'' +
                ", threshold=" + threshold +
                ", detectionFrames=" + detectionFrames +
                ", blockingFrames=" + blockingFrames +
                '}';
    }
}
