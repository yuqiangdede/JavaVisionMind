package com.yuqiangdede.asr.dto.output;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class RuntimeHotwordStore {

    private List<String> baseTerms = new ArrayList<>();
}
