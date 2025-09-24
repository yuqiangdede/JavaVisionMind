package com.yuqiangdede.ffe.dto.input;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;


@Setter
@Getter
public class Input4Search {

    /**
     * 图片url
     */
    private final String imgUrl;

    /**
     * 人脸分组
     */
    private final String groupId;

    /**
     * 人脸分数阈值，用于添加和搜索的时候，当小于这个值的时候，直接不返回
     */
    @JsonProperty("faceScoreThreshold")
    @JsonInclude(JsonInclude.Include.NON_NULL) // 只序列化非空字段
    private Float faceScoreThreshold;

    /**
     * 搜索的时候相似度的阈值，默认值为 0.5
     */
    @JsonProperty("confidenceThreshold")
    @JsonInclude(JsonInclude.Include.NON_NULL) // 只序列化非空字段
    private Float confidenceThreshold;


    /**
     * Jackson 反序列化构造方法
     */
    @JsonCreator
    public Input4Search(
            @JsonProperty("imgUrl") String imgUrl,
            @JsonProperty("groupId") String groupId,
            @JsonProperty("faceScoreThreshold") Float faceScoreThreshold,
            @JsonProperty("confidenceThreshold") Float confidenceThreshold) {
        this.imgUrl = imgUrl;
        this.groupId = groupId;
        this.faceScoreThreshold = (faceScoreThreshold != null) ? faceScoreThreshold : 0.5f;
        this.confidenceThreshold = (confidenceThreshold != null) ? confidenceThreshold : 0.45f;
    }


    @Override
    public String toString() {
        return "Input4Search{" +
                "imgUrl='" + imgUrl + '\'' +
                ", groupId='" + groupId + '\'' +
                ", faceScoreThreshold=" + faceScoreThreshold +
                ", confidenceThreshold=" + confidenceThreshold +
                '}';
    }
}
