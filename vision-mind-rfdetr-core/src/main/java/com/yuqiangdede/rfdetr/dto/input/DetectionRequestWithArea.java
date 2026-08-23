package com.yuqiangdede.rfdetr.dto.input;

import com.yuqiangdede.common.dto.Point;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.ArrayList;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class DetectionRequestWithArea extends DetectionRequest {

    private ArrayList<ArrayList<Point>> detectionFrames;
    private ArrayList<ArrayList<Point>> blockingFrames;
}
