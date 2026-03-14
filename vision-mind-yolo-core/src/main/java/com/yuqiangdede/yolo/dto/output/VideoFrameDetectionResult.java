package com.yuqiangdede.yolo.dto.output;

import com.yuqiangdede.common.dto.output.Box;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class VideoFrameDetectionResult {

    /**
     * 当前读取帧号（从1开始）。
     */
    private int frameIndex;

    /**
     * 视频时间戳（毫秒）。
     */
    private long timestampMs;

    /**
     * 当前帧推理耗时（毫秒）。
     */
    private long costMs;

    /**
     * 当前帧检测框。
     */
    private List<Box> boxes;
}
