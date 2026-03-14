package com.yuqiangdede.asr.service;

import com.yuqiangdede.asr.dto.output.AsrAppliedRule;
import com.yuqiangdede.asr.dto.output.PhraseRuleItem;
import com.yuqiangdede.asr.dto.output.PostProcessResult;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

@Service
public class AsrPostProcessService {

    private static final Pattern CJK_SPACING = Pattern.compile("(?<=[\\u4e00-\\u9fff0-9])\\s+(?=[\\u4e00-\\u9fff0-9])");
    private static final Pattern CJK_PUNCT_A = Pattern.compile("(?<=[\\u4e00-\\u9fff0-9])\\s+(?=[，。！？；：、】【《》“”‘’（）])");
    private static final Pattern CJK_PUNCT_B = Pattern.compile("(?<=[，。！？；：、】【《》“”‘’（）])\\s+(?=[\\u4e00-\\u9fff0-9])");
    private static final Pattern MULTI_SPACE = Pattern.compile("\\s{2,}");

    public PostProcessResult process(String rawText, List<PhraseRuleItem> rules) {
        String original = rawText == null ? "" : rawText;
        List<AsrAppliedRule> appliedRules = new ArrayList<>();
        String normalized = normalizeText(original);
        if (!normalized.equals(original)) {
            appliedRules.add(new AsrAppliedRule("phrase", "normalize_asr_spacing", original, normalized, original, normalized));
        }

        String current = normalized;
        List<PhraseRuleItem> sortedRules = new ArrayList<>(rules == null ? List.of() : rules);
        sortedRules.sort(Comparator.comparingInt(this::maxPatternLength).reversed());
        for (PhraseRuleItem rule : sortedRules) {
            for (String pattern : sortPatterns(rule.getPatterns())) {
                if (!current.contains(pattern)) {
                    continue;
                }
                String before = current;
                current = current.replace(pattern, rule.getReplacement());
                appliedRules.add(new AsrAppliedRule("phrase", rule.getName(), pattern, rule.getReplacement(), before, current));
            }
        }

        return new PostProcessResult(normalized, current, appliedRules);
    }

    public String normalizeText(String text) {
        String current = text == null ? "" : text;
        if (current.isEmpty() || !current.contains(" ")) {
            return current;
        }
        current = CJK_SPACING.matcher(current).replaceAll("");
        current = CJK_PUNCT_A.matcher(current).replaceAll("");
        current = CJK_PUNCT_B.matcher(current).replaceAll("");
        current = MULTI_SPACE.matcher(current).replaceAll(" ").trim();
        return current;
    }

    private int maxPatternLength(PhraseRuleItem item) {
        int max = 0;
        if (item.getPatterns() == null) {
            return max;
        }
        for (String pattern : item.getPatterns()) {
            if (pattern != null) {
                max = Math.max(max, pattern.trim().length());
            }
        }
        return max;
    }

    private List<String> sortPatterns(List<String> patterns) {
        List<String> sorted = new ArrayList<>(patterns == null ? List.of() : patterns);
        sorted.sort(Comparator.comparingInt(String::length).reversed());
        return sorted;
    }
}
