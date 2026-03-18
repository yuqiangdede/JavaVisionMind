package com.yuqiangdede.asr.service;

import com.yuqiangdede.asr.dto.output.AsrHealthResponse;
import com.yuqiangdede.asr.dto.output.AsrTranscribeResponse;
import com.yuqiangdede.asr.dto.output.PhraseRuleItem;
import com.yuqiangdede.asr.dto.output.PostProcessResult;
import com.yuqiangdede.platform.common.util.InMemoryMultipartFile;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AsrService {

    private final AudioDecodeService audioDecodeService;
    private final SherpaOnnxAsrService sherpaOnnxAsrService;
    private final SherpaOnnxPunctuationService sherpaOnnxPunctuationService;
    private final AsrConfigService asrConfigService;
    private final AsrPostProcessService asrPostProcessService;
    private final AsrPathResolver pathResolver;

    public AsrHealthResponse health() {
        return new AsrHealthResponse(
                sherpaOnnxAsrService.isReady(),
                pathResolver.getModelDir().toString(),
                pathResolver.getConfigDir().toString(),
                pathResolver.getUploadDir().toString(),
                pathResolver.getRuntimeJavaJar().toString(),
                pathResolver.getRuntimeNativeJar().toString(),
                sherpaOnnxAsrService.runtimeMessage()
        );
    }

    public AsrTranscribeResponse transcribe(MultipartFile file, boolean enablePunctuation) {
        List<String> mergedHotwords = mergeHotwords(asrConfigService.getBaseHotwords());
        var decodedAudio = audioDecodeService.decode(file);
        String rawText = sherpaOnnxAsrService.transcribe(decodedAudio.getSamples(), mergedHotwords);
        List<PhraseRuleItem> rules = asrConfigService.getPhraseRules();
        PostProcessResult postProcess = asrPostProcessService.process(rawText, rules);
        String correctedText = postProcess.getTextAfterPhrase();
        if (enablePunctuation) {
            correctedText = sherpaOnnxPunctuationService.addPunctuation(correctedText);
        }
        return new AsrTranscribeResponse(
                postProcess.getRawText(),
                correctedText,
                postProcess.getAppliedRules(),
                decodedAudio.getAudioInfo(),
                mergedHotwords,
                enablePunctuation
        );
    }

    public AsrTranscribeResponse transcribe(byte[] bytes, String fileName, String contentType, boolean enablePunctuation) {
        MultipartFile multipartFile = new InMemoryMultipartFile("file", fileName, contentType, bytes);
        return transcribe(multipartFile, enablePunctuation);
    }

    private List<String> mergeHotwords(List<String> baseTerms) {
        Set<String> values = new LinkedHashSet<>();
        append(values, baseTerms);
        return new ArrayList<>(values);
    }

    private void append(Set<String> values, List<String> items) {
        if (items == null) {
            return;
        }
        for (String item : items) {
            String normalized = item == null ? "" : item.trim();
            if (!normalized.isEmpty()) {
                values.add(normalized);
            }
        }
    }
}
