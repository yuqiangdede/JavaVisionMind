package com.yuqiangdede.tbir.dto.input;

import lombok.Data;

@Data
public class ImgSearchUrlRequest {
    private String imgUrl;
    private Integer topN;
}

