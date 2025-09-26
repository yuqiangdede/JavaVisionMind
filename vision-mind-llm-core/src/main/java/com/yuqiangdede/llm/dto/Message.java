package com.yuqiangdede.llm.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;

@Data
public class Message {
    private String message;

    @JsonAlias({"img", "imgUrl"})
    private String imageUrl;

    private String system;
}
