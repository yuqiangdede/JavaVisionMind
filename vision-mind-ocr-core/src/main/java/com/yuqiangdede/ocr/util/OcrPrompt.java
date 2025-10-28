package com.yuqiangdede.ocr.util;

import com.yuqiangdede.common.util.JsonUtils;
import com.yuqiangdede.llm.service.LLMService;
import com.yuqiangdede.ocr.dto.output.OcrDetectionResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
@Slf4j
@RequiredArgsConstructor
public class OcrPrompt {

    private final LLMService lLMService;
    private static final Pattern THOUGHT_BLOCK_PATTERN = Pattern.compile("(?is)<(?:think|thinking)>.*?</(?:think|thinking)>");
    private static final Pattern THOUGHT_TAG_PATTERN = Pattern.compile("(?is)</?(?:think|thinking)>");
    private static final Pattern CODE_FENCE_PATTERN = Pattern.compile("(?s)```(?:json)?\\s*(.*?)```");

    /**
     * 语义重建模式，考虑是否需要拆分或合并 OCR 的检测结果。
     */
    public String semanticReconstruction(List<OcrDetectionResult> detections) throws IOException {
        String detectionString = detections.stream()
                .map(OcrDetectionResult::getText)
                .map(s -> s == null ? "" : s
                        .replaceAll("[^\\p{L}\\p{N}\\p{P}\\p{Z}]", "")
                        .trim()
                )
                .collect(Collectors.joining("\n"));

        String queryRequest = "你是OCR文本修正器。\n" +
                "\n" +
                "任务：接受OCR识别出来的文本，往往有一些错误，需要参考全文进行修正错误，调整顺序等。\n" +
                "\n" +
                "【允许的修正规则（仅明显错误）】\n" +
                "1) 常见错别字/笔误：如“份限公司”→“有限公司”，“光生”→“先生”。\n" +
                "2) 字母数字混淆：0↔O、1↔I、5↔S 等（仅在上下文明显时修正），如“Q/EML-0O004”→“Q/EML-00004”或“Q/EML-0O004”→“Q/EML-0O004”仅在明确时替换。\n" +
                "3) 单位补全/规范：数字+“k”→“kg”（体重、重量等明确语境）；中英文标点合理规范（如全/半角统一，“：”前后空格规范）。\n" +
                "4) 明显分词/断裂错误合并：不改变语义前提下的直观修复。\n" +
                "5) 明确品牌/实体大小写/连字符规范（e.g., “e-Hualuy”若能确定应为“e-Huali”才改；不确定则跳过）。\n" +
                "6) 明显的语序错误\n" +
                "\n" +
                "【禁止事项】\n" +
                "- 不做主观意译、润色或信息猜测（如把“填表日期234日”猜成具体年月日）。\n" +
                "- 语义不确定、行业专有名词不确定时，**宁可不改**。\n" +
                "\n" +
                "【示例（示意，不代表你这批一定如此处理）】\n" +
                "输入：\n" +
                "\t茂名职业技术学院出差申请表\n" +
                "\t2016年3月17日\n" +
                "\t部门：成教部\n" +
                "\t刘明波\n" +
                "\t出差人员\n" +
                "\t电白\n" +
                "\t出差日期2016年月20日至2016年3月2日\n" +
                "\t出差地点\n" +
                "\t206年30到电自村班教影点上今镇行政管理课程。\n" +
                "\t出差事由\n" +
                "\t2016.3.17\n" +
                "\t部门负责人审批\n" +
                "\t分管领导审批\n" +
                "\t院长或书记审批\n" +
                "输出(仅输出修改之后的输入内容即可，不需要增加修改说明的辅助信息)：\n" +
                "\t茂名职业技术学院出差申请表\n" +
                "\t2016年3月17日\n" +
                "\t部门：成教部\n" +
                "\t出差人员：刘明波\n" +
                "\t出差日期：2016年3月20日至2016年3月20日\n" +
                "\t出差地点：电白\n" +
                "\t出差事由：2016年3月20日到电自村班教学点上乡镇行政管理课程。\n" +
                "\t2016.3.17\n" +
                "\t部门负责人审批\n" +
                "\t分管领导审批\n" +
                "\t院长或书记审批\n" +
                "\t\n" +
                "现在开始处理以下输入。\n" + detectionString;


        log.info("LLM SemanticReconstruction Request : {} ", queryRequest);
        String chatResponse = lLMService.chat(queryRequest);
        log.info("LLM SemanticReconstruction Response : {} ", chatResponse);

        if (chatResponse.isEmpty()) {
            log.warn("LLM SemanticReconstruction response empty after sanitization, fallback to detection payload");
        }
        return chatResponse;
    }

    public String fineTuning(List<OcrDetectionResult> detections) throws IOException {
        String detectionJson = JsonUtils.object2Json(detections);
        String queryRequest = "请润色以下 OCR 识别文本\n" +
                "任务：请仅针对每个 text 字段进行纠错或补全，使文本更加通顺自然。输出 JSON，结构与输入完全一致。\n" +
                "\n" +
                "增强调教\n" +
                "- 只允许修正 text 字段，不得修改 confidence、polygon 等其他字段。\n" +
                "- 保持 JSON 字段顺序和格式不变，请勿包裹额外说明文字。\n" +
                "- 常见问题示例：\n" +
                "  1) 公司或机构名称识别不完整或有错别字时，请根据语境修正，例如“有限公同”应为“有限公司”。\n" +
                "  2) 字母与数字混淆时请判断上下文纠正，例如“Q/EML-0O004”对应“Q/EML-00004”。\n" +
                "  3) 单位或标点缺失时补齐，如“+2k”改为“+ 2 kg”，“kg”与数字之间保留空格。\n" +
                "  4) 英文大小写、连字符、空格要符合行业规范，例如“e-Hua luy”改为“e-Hualuy”。\n" +
                "\n" +
                "禁止项\n" +
                "- 禁止臆造未在原始文本出现的信息（如凭空补充颜色、型号）。\n" +
                "- 遇到确实无法判断的词语，请保持原样，不要乱写。\n" +
                "\n" +
                "示例输入\n" +
                "[{\"confidence\":0.9640813563019037,\"polygon\":[{\"x\":749.67285,\"y\":25.17282},{\"x\":919.82715,\"y\":25.17282},{\"x\":919.82715,\"y\":38.95218},{\"x\":749.67285,\"y\":38.95218}],\"text\":\"学术活动中专业的成绩表现\"}]\n" +
                "示例输出\n" +
                "[{\"confidence\":0.9640813563019037,\"polygon\":[{\"x\":749.67285,\"y\":25.17282},{\"x\":919.82715,\"y\":25.17282},{\"x\":919.82715,\"y\":38.95218},{\"x\":749.67285,\"y\":38.95218}],\"text\":\"学术活动中专业的成绩表现\"}]\n" +
                "\n" +
                "请开始处理，直接输出 JSON："
                + detectionJson;

        log.info("LLM QueryRequest : {} ", queryRequest);
        String chatResponse = lLMService.chat(queryRequest);
        log.info("LLM Response : {} ", chatResponse);

        String sanitized = sanitizeChatResponse(chatResponse);
        if (sanitized.isEmpty()) {
            log.warn("LLM response empty after sanitization, fallback to detection payload");
            return detectionJson;
        }
        return sanitized;
    }

    private String sanitizeChatResponse(String chatResponse) {
        if (chatResponse == null || chatResponse.isBlank()) {
            return "";
        }
        String withoutThoughts = THOUGHT_BLOCK_PATTERN.matcher(chatResponse).replaceAll("");
        String withoutTags = THOUGHT_TAG_PATTERN.matcher(withoutThoughts).replaceAll("");
        Matcher codeFenceMatcher = CODE_FENCE_PATTERN.matcher(withoutTags);
        if (codeFenceMatcher.find()) {
            return codeFenceMatcher.group(1).trim();
        }
        return withoutTags.trim();
    }
}
