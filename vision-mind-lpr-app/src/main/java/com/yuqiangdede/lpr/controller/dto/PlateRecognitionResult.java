package com.yuqiangdede.lpr.controller.dto;

import com.yuqiangdede.common.dto.output.Box;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlateRecognitionResult {

    private Box box;

    /**
     * 识别出的车牌字符串
     */
    private String plate;
}
