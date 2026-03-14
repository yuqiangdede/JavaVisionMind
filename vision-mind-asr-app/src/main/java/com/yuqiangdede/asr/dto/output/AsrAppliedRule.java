package com.yuqiangdede.asr.dto.output;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AsrAppliedRule {

    private String stage;
    private String rule;
    private String pattern;
    private String replacement;
    private String before;
    private String after;
}
