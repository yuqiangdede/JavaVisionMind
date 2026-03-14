package com.yuqiangdede.asr.dto.output;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class PostProcessResult {

    private String rawText;
    private String textAfterPhrase;
    private List<AsrAppliedRule> appliedRules;
}
