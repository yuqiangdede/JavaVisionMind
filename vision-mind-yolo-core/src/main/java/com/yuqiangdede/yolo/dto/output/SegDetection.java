package com.yuqiangdede.yolo.dto.output;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.yuqiangdede.common.dto.Point;
import com.yuqiangdede.common.util.ImageUtil;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Rect;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SegDetection {
    public Rect box;
    public float score;
    public int classId;
    @JsonIgnore
    public Mat mask;
    public List<List<Point>> points = new ArrayList<>(); // 一个实例可能有多个轮廓，一个轮廓又有多个点

    public SegDetection(Rect box, float score, int classId, Mat mask) {
        this.box = box;
        this.score = score;
        this.classId = classId;
        this.mask = mask;

        // 创建存储轮廓的列表
        List<MatOfPoint> contours = new ArrayList<>();
           // 查找轮廓
        Imgproc.findContours(mask, contours, new Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

        for (MatOfPoint contour : contours) {
            List<org.opencv.core.Point> points = contour.toList();

            List<Point> pointList = new ArrayList<>();
            for (org.opencv.core.Point point : points) {
                pointList.add(new Point((float) point.x, (float) point.y));
            }
            this.points.add(pointList);
        }
    }
}
