package com.yuqiangdede.common.dto.output;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.Optional;


/**
 * 坐标框（没有做归一化处理的，四象限坐标数据）
 * 置信度，按照实际情况自行过滤
 * 目标类型
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Box {
    /**
     * 目标框左上角坐标 原始坐标 因为有缩放处理，已经不是整形了，虽然含义还是整形的像素点
     */
    private float x1;
    /**
     * 目标框左上角坐标 原始坐标
     */
    private float y1;
    /**
     * 目标框右下角坐标 原始坐标
     */
    private float x2;
    /**
     * 目标框右下角坐标 原始坐标
     */
    private float y2;

    /**
     * 置信度阈值
     */
    private float conf;
    /**
     * 目标类型描述
     */
    private String typeName;
    /**
     * 目标类型
     */
    private int type;

    public Box(float x1, float y1, float x2, float y2) {
        this.x1 = x1;
        this.y1 = y1;
        this.x2 = x2;
        this.y2 = y2;
    }

    /**
     * 构造函数 姿态估计使用，记得使用之后后面要把关键点注入
     */
    public Box(Float x1, Float y1, Float x2, Float y2, Float conf) {
        this.x1 = x1;
        this.y1 = y1;
        this.x2 = x2 ;
        this.y2 = y2;
        this.conf = conf;
        this.type = 0;
        this.typeName = "";
    }


    /**
     * @param x1        原始坐标
     * @param y1        原始坐标
     * @param x2        原始坐标
     * @param y2        原始坐标
     * @param conf       置信度
     * @param type       类型
     * @param classNames 类型枚举
     */
    public Box(Float x1, Float y1, Float x2, Float y2, Float conf, Float type, Map<Integer, String> classNames) {
        this.x1 = x1;
        this.y1 = y1;
        this.x2 = x2;
        this.y2 = y2;
        this.conf = conf;
        this.type = type.intValue();
        this.typeName = Optional.ofNullable(classNames.get(type.intValue()))
                .map(s -> s.replace("'", ""))
                .orElse(null);

    }

    /**
     * 判断给定宽度和高度是否在最小值和最大值范围内
     *
     * @param minSize 最小值
     * @param maxSize 最大值
     * @return 如果宽度和高度都在最小值和最大值范围内，则返回true，否则返回false
     */
    public boolean isValid(float minSize, float maxSize) {
        float width = x2 - x1;
        float height = y2 - y1;
        return width >= minSize && width <= maxSize &&
                height >= minSize && height <= maxSize;
    }
}
