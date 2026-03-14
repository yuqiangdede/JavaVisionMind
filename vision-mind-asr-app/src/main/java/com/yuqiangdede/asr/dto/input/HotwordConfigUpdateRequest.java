package com.yuqiangdede.asr.dto.input;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class HotwordConfigUpdateRequest {

    private List<String> baseTerms = new ArrayList<>();
}
