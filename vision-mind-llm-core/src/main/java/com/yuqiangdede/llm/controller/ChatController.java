package com.yuqiangdede.llm.controller;


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
    public String translate(@RequestBody Message msg) {
        String chatResponse;
        try {
            chatResponse = lLMService.chat("You are a translator who cherishes words like gold." +
                    "Translate the following Chinese text to English: " + msg.getMessage() + "." +
                    "Directly return the translated English without any additional description." +
                    "For example, input：Translate the following Chinese text to English: 红色盒子." +
                    "output：red box.");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        System.out.println("content = " + chatResponse);
        return chatResponse;
    }

    @PostMapping("/chat")
    public String chat(@RequestBody Message msg) throws IOException {
        String chatResponse = lLMService.chat(msg.getMessage());
        System.out.println("content = " + chatResponse);
        return chatResponse;
    }
    @PostMapping("/chatWithImg")
    public String chatWithImg(@RequestBody Message msg) throws IOException {

             String chatResponse = lLMService.chatWithImg(msg.getMessage() ,msg.getImg());
        System.out.println("content = " + chatResponse);
        return chatResponse;
    }

}