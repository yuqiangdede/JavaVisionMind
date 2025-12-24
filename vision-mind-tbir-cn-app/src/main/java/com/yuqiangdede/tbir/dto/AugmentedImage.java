package com.yuqiangdede.tbir.dto;

import com.yuqiangdede.common.dto.output.Box;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.awt.image.BufferedImage;

@Data
@AllArgsConstructor
public class AugmentedImage {
    private Box originalBox;          // 检测框（扩展前）
    private Box croppedBox;           // 实际裁剪框（扩展后）
    private BufferedImage image;      // 子图图像
    private String augmentationType;  // 增强方式，如 "pad10", "rotate15", "flipH"
}
