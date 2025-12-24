package com.yuqiangdede.tbir.dto;

import com.yuqiangdede.common.dto.output.Box;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Map;

@Data
@AllArgsConstructor
public class LuceHit {

    private String imageId;         // 主图ID
    private String imageUrl;        // 主图URL（从 Lucene StoredField 取出）
    private Box box;                // 子图的检测框
    private float score;            // 相似度（CLIP向量之间计算）
    private String cameraId;        // 可选监控点ID
    private String groupId;         // 可选分组
    private Map<String, String> meta; // 主图的元信息（可用于UI展示或业务判断）
}
