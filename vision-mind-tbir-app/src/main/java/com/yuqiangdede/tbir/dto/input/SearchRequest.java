package com.yuqiangdede.tbir.dto.input;

import lombok.Data;


@Data
public class SearchRequest {


    /**
     * 搜索关键词（文本描述）
     */
    private String query;

    /**
     * 监控点IndexCode
     */
    private String cameraId;

    /**
     * 图片分组ID，用于区分不同业务场景的图片
     */
    private String groupId;

    /**
     * 返回前 topN 个匹配结果，默认建议 10
     */
    private Integer topN;

}
