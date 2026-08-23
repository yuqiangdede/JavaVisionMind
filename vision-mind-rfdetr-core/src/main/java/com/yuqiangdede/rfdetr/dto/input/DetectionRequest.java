package com.yuqiangdede.rfdetr.dto.input;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DetectionRequest {

    private String imgUrl;
    private Float threshold;
    private String types;
}
