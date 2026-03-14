package com.yuqiangdede.asr.dto.output;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class AsrTranscribeResponse {

    private String rawText;
    private String textAfterPhrase;
    private List<AsrAppliedRule> appliedRules;
    private AsrAudioInfo audioInfo;
    private List<String> hotwords;
    private boolean punctuationEnabled;
}
