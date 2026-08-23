package com.yuqiangdede.rfdetr.dto.output;

import com.yuqiangdede.common.dto.output.Box;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class VideoFrameDetectionResult {

    private int frameIndex;
    private long timestampMs;
    private long costMs;
    private List<Box> boxes;
}
