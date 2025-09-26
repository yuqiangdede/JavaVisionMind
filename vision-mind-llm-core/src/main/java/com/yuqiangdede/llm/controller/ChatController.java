package com.yuqiangdede.llm.controller;


import com.yuqiangdede.common.dto.output.HttpResult;
import com.yuqiangdede.llm.dto.Message;
import com.yuqiangdede.llm.service.LLMService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.io.IOException;

@RestController
@RequestMapping("/api")
@Slf4j
@RequiredArgsConstructor
public class ChatController {

    private final LLMService lLMService;

    @PostMapping("/translate")
    public HttpResult<String> translate(@RequestBody Message msg) {
        try {
            String chatResponse = lLMService.chat("You are a translator who cherishes words like gold." +
                    "Translate the following Chinese text to English: " + msg.getMessage() + "." +
                    "Directly return the translated English without any additional description." +
                    "For example, input：Translate the following Chinese text to English: 红色盒子." +
                    "output：red box.");
            log.info("translate response={}", chatResponse);
            return new HttpResult<>(true, chatResponse);
        } catch (IOException e) {
            log.error("translate failed", e);
            return new HttpResult<>(false, e.getMessage());
        }
    }

    @PostMapping("/chat")
    public HttpResult<String> chat(@RequestBody Message msg) {
        try {
            String chatResponse = lLMService.chat(msg.getMessage());
            log.info("chat response={}", chatResponse);
            return new HttpResult<>(true, chatResponse);
        } catch (IOException e) {
            log.error("chat failed", e);
            return new HttpResult<>(false, e.getMessage());
        }
    }
    @PostMapping("/chatWithImg")
    public HttpResult<String> chatWithImg(@RequestBody Message msg) {
        try {
            String chatResponse = lLMService.chatWithImg(msg.getMessage(), msg.getImageUrl(), msg.getSystem());
            log.info("chatWithImg response={}", chatResponse);
            return new HttpResult<>(true, chatResponse);
        } catch (IOException | RuntimeException e) {
            log.error("chatWithImg failed", e);
            return new HttpResult<>(false, e.getMessage());
        }
    }

}
