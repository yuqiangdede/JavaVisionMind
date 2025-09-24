package com.yuqiangdede.yolo.dto.input;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class VideoInput {
    private String rtspUrl;
    // 视频帧数，只分析这些帧就结束
    private Integer frameNum;
    private Float conf;
    private String types;


}
