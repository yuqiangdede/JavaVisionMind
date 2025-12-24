package com.yuqiangdede.tbir.dto.input;

import lombok.Data;

@Data
public class SimilarityTextImageRequest {
    private String text;
    private String imgUrl;
}

