package com.yuqiangdede.yolo.dto.input;

import com.yuqiangdede.common.dto.Point;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.ArrayList;

@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DetectionRequestWithArea extends DetectionRequest {

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
        return "DetectionRequestWithArea{" +
                "detectionFrames=" + detectionFrames +
                ", blockingFrames=" + blockingFrames +
                "} " + super.toString();
    }
}
