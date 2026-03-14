package com.yuqiangdede.yolo.dto.input;


import com.yuqiangdede.common.dto.Point;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class VideoInput {

    /**
     * 视频流地址（RTSP/HTTP/本地文件路径均可）。
     */
    private String rtspUrl;
    /**
     * 读取并分析的总帧数上限，默认100。
     */
    private Integer frameNum;

    /**
     * 逐帧分析间隔，默认取配置 frame.interval。
     */
    private Integer frameInterval;

    /**
     * 置信度阈值，可选。
     */
    private Float conf;

    /**
     * 类型过滤，可选。
     */
    private String types;

    /**
     * 检测区域，可选。
     */
    private ArrayList<ArrayList<Point>> detectionFrames;

    /**
     * 屏蔽区域，可选。
     */
    private ArrayList<ArrayList<Point>> blockingFrames;

}
