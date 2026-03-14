package com.yuqiangdede.asr.dto.output;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class PhraseRuleStore {

    private List<PhraseRuleItem> rules = new ArrayList<>();
}
