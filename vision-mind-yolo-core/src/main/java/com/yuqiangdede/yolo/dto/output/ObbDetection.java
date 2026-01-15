package com.yuqiangdede.yolo.dto.output;

import com.yuqiangdede.common.dto.Point;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ObbDetection {
    private float centerX;
    private float centerY;
    private float width;
    private float height;
    private float angle;
    private float score;
    private int classId;
    private String className;
    private List<Point> points = new ArrayList<>();
}
