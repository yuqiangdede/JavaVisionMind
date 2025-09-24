package com.yuqiangdede.tbir.util;

import com.yuqiangdede.llm.service.LLMService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static com.yuqiangdede.tbir.config.Constant.KEY_NUM;

@Component
@Slf4j
@RequiredArgsConstructor
public class PromptExpand {

    private final LLMService lLMService;

    /**
     * 多角度扩展 query（prompt）
     */
    public List<String> expand(String query) {
        try {
            String queryRequest = "You are an excellent translator who can ensure the original meaning without excessive rendering." +
                    "Translate the following Chinese text into English in " + KEY_NUM + " ways: " + query + "." +
                    "Directly return the translated English without any additional explanation. Five translation uses | separated." +
                    "For example, input：Translate the following Chinese text into English in five ways: 吹气球的人" +
                    "output：A person blowing a balloon|Someone inflating a balloon|A balloon blower|An individual puffing up a balloon|A man blowing up a balloon";
            log.info("LLM QueryRequest : {} ", queryRequest);
            String chatResponse = lLMService.chat(queryRequest);
            log.info("LLM Response : {} ", chatResponse);
            return Arrays.asList(chatResponse.trim().split("\\||\\n"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 可选：随机取前 N 个，防止 prompt 太多
     */
    public List<String> expandTopN(String query, int topN) {
        List<String> all = expand(query);
        return all.subList(0, Math.min(topN, all.size()));
    }
}
