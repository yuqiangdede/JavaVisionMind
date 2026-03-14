package com.yuqiangdede.asr.dto.output;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AsrAudioInfo {

    private String sourceFormat;
    private int sampleRate;
    private int channels;
    private int sampleCount;
    private long durationMs;
}
