package com.yuqiangdede.common.dto.output;

import com.yuqiangdede.common.dto.Point;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;


@EqualsAndHashCode(callSuper = false)
@Data
@AllArgsConstructor
public class BoxWithKeypoints extends Box {

    /**
     * 关键点信息
     * 关键点信息 0:鼻尖1:右边眼睛2:左边眼睛3:右边耳朵4:左边耳朵5:右边肩膀6:左边肩膀7:右侧胳膊肘8:左侧胳膊肘9:右侧手腕10左侧手腕11右侧胯12左侧胯13右侧膝盖14左侧膝盖15右侧脚踝16左侧脚踝
     */
    private List<Point> keypoints = null;

    public BoxWithKeypoints(Float x1, Float y1, Float x2, Float y2, Float conf) {
        super(x1, y1, x2, y2, conf);
    }
    /**
     * 注入关键点信息，关键点的格式是 x y 置信度 x y 置信度 这种三个一组
     *
     * @param keypoints 关键点
     * @param height    图片高度
     * @param width     图片宽度
     */
    public void injectKeypoints(List<Float> keypoints, int height, int width) {
        this.keypoints = new java.util.ArrayList<>();
        for (int i = 0; i < keypoints.size(); i += 3) {
            Point point = new Point(keypoints.get(i), keypoints.get(i + 1));
            this.keypoints.add(point);
        }

    }

}
