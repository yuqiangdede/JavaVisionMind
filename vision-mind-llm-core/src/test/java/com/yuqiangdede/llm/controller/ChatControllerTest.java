package com.yuqiangdede.llm.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuqiangdede.llm.dto.Message;
import com.yuqiangdede.llm.service.LLMService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ChatController.class)
class ChatControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private LLMService lLMService;

    @Test
    void translate_returnsResult() throws Exception {
        when(lLMService.chat(anyString())).thenReturn("translated");

        mockMvc.perform(post("/api/translate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(writeJson(message("hello"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }

    @Test
    void chat_returnsResult() throws Exception {
        when(lLMService.chat(anyString())).thenReturn("reply");

        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(writeJson(message("hi"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }

    @Test
    void chatWithImg_returnsResult() throws Exception {
        when(lLMService.chatWithImg(anyString(), anyString(), anyString())).thenReturn("reply");

        Message msg = new Message();
        msg.setMessage("describe");
        msg.setImageUrl("http://example.com/img.jpg");
        msg.setSystem("sys");

        mockMvc.perform(post("/api/chatWithImg")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(writeJson(msg)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }

    private Message message(String text) {
        Message msg = new Message();
        msg.setMessage(text);
        return msg;
    }

    private String writeJson(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}
