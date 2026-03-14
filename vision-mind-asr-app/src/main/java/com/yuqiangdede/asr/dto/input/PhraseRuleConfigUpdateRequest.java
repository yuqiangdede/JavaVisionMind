package com.yuqiangdede.asr.dto.input;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class PhraseRuleConfigUpdateRequest {

    private List<String> lines = new ArrayList<>();
}
