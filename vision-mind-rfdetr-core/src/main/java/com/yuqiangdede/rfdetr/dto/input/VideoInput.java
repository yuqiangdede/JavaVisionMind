package com.yuqiangdede.rfdetr.dto.input;

import com.yuqiangdede.common.dto.Point;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class VideoInput {

    private String rtspUrl;
    private Integer frameNum;
    private Integer frameInterval;
    private Float conf;
    private String types;
    private ArrayList<ArrayList<Point>> detectionFrames;
    private ArrayList<ArrayList<Point>> blockingFrames;
}
