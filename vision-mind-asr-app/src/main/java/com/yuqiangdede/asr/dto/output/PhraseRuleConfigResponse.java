package com.yuqiangdede.asr.dto.output;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class PhraseRuleConfigResponse {

    private List<PhraseRuleItem> rules;
}
