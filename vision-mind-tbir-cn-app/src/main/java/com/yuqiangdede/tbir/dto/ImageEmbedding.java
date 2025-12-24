package com.yuqiangdede.tbir.dto;

import com.yuqiangdede.common.dto.output.Box;
import lombok.AllArgsConstructor;
import lombok.Data;


@Data
@AllArgsConstructor
public class ImageEmbedding {
    private Box sourceBox;          // 原始框（扩展前）
    private Box cropBox;           // 实际子图裁剪框（扩展后）
    private String augmentationType;// 增强类型（如 original, rotate15 等）
    private float[] vector;           // 子图对应的向量
    private boolean isMainImage;   // 是否是主图
}
