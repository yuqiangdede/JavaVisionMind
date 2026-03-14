package com.yuqiangdede.asr.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.yuqiangdede.asr.dto.output.PhraseRuleItem;
import com.yuqiangdede.asr.dto.output.PhraseRuleStore;
import com.yuqiangdede.asr.dto.output.RuntimeHotwordStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AsrConfigService {

    private final AsrPathResolver pathResolver;
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

    public synchronized List<String> getBaseHotwords() {
        RuntimeHotwordStore store = readHotwordStore();
        return deduplicate(store.getBaseTerms());
    }

    public synchronized List<String> saveBaseHotwords(List<String> baseTerms) {
        RuntimeHotwordStore store = new RuntimeHotwordStore();
        store.setBaseTerms(deduplicate(baseTerms));
        writeYaml(pathResolver.hotwordsConfigFile(), store);
        return store.getBaseTerms();
    }

    public synchronized List<PhraseRuleItem> getPhraseRules() {
        PhraseRuleStore store = readPhraseRuleStore();
        return normalizeRules(store.getRules());
    }

    public synchronized List<PhraseRuleItem> savePhraseRules(List<String> lines) {
        List<PhraseRuleItem> rules = new ArrayList<>();
        int index = 1;
        for (String line : lines) {
            String value = String.valueOf(line).trim();
            if (value.isEmpty()) {
                continue;
            }
            if (!value.contains("=>")) {
                throw new IllegalArgumentException("规则格式错误，应为：误词1, 误词2 => 正确词");
            }
            String[] split = value.split("=>", 2);
            List<String> patterns = new ArrayList<>();
            for (String rawPattern : split[0].split(",")) {
                String pattern = rawPattern.trim();
                if (!pattern.isEmpty()) {
                    patterns.add(pattern);
                }
            }
            String replacement = split[1].trim();
            if (patterns.isEmpty() || replacement.isEmpty()) {
                throw new IllegalArgumentException("规则格式错误，应为：误词1, 误词2 => 正确词");
            }
            rules.add(new PhraseRuleItem(String.format("phrase_rule_%03d", index++), patterns, replacement));
        }

        PhraseRuleStore store = new PhraseRuleStore();
        store.setRules(normalizeRules(rules));
        writeYaml(pathResolver.phraseRulesConfigFile(), store);
        return store.getRules();
    }

    private RuntimeHotwordStore readHotwordStore() {
        try {
            Path file = pathResolver.hotwordsConfigFile();
            if (!Files.exists(file)) {
                return new RuntimeHotwordStore();
            }
            RuntimeHotwordStore store = yamlMapper.readValue(file.toFile(), RuntimeHotwordStore.class);
            return store == null ? new RuntimeHotwordStore() : store;
        } catch (Exception e) {
            throw new IllegalStateException("读取热词配置失败", e);
        }
    }

    private PhraseRuleStore readPhraseRuleStore() {
        try {
            Path file = pathResolver.phraseRulesConfigFile();
            if (!Files.exists(file)) {
                return new PhraseRuleStore();
            }
            PhraseRuleStore store = yamlMapper.readValue(file.toFile(), PhraseRuleStore.class);
            return store == null ? new PhraseRuleStore() : store;
        } catch (Exception e) {
            throw new IllegalStateException("读取近音词配置失败", e);
        }
    }

    private void writeYaml(Path path, Object value) {
        try {
            Files.createDirectories(path.getParent());
            yamlMapper.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), value);
        } catch (Exception e) {
            throw new IllegalStateException("写入配置失败: " + path, e);
        }
    }

    private List<String> deduplicate(List<String> values) {
        Set<String> deduplicated = new LinkedHashSet<>();
        if (values != null) {
            for (String value : values) {
                String normalized = String.valueOf(value).trim();
                if (!normalized.isEmpty()) {
                    deduplicated.add(normalized);
                }
            }
        }
        return new ArrayList<>(deduplicated);
    }

    private List<PhraseRuleItem> normalizeRules(List<PhraseRuleItem> rules) {
        List<PhraseRuleItem> normalized = new ArrayList<>();
        if (rules == null) {
            return normalized;
        }
        for (PhraseRuleItem item : rules) {
            if (item == null) {
                continue;
            }
            List<String> patterns = deduplicate(item.getPatterns());
            String replacement = item.getReplacement() == null ? "" : item.getReplacement().trim();
            String name = item.getName() == null ? "" : item.getName().trim();
            if (!patterns.isEmpty() && !replacement.isEmpty()) {
                normalized.add(new PhraseRuleItem(name.isEmpty() ? "phrase_rule" : name, patterns, replacement));
            }
        }
        return normalized;
    }
}
