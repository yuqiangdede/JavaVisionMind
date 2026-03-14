package com.yuqiangdede.asr.dto.output;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AsrDecodeAudio {

    private float[] samples;
    private AsrAudioInfo audioInfo;
}
