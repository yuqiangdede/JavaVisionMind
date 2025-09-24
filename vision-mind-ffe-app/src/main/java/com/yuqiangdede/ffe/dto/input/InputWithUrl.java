package com.yuqiangdede.ffe.dto.input;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;


@Setter
@Getter
public class InputWithUrl {

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
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private final Float faceScoreThreshold;


    @JsonCreator
    public InputWithUrl(
            @JsonProperty("imgUrl") String imgUrl,
            @JsonProperty("groupId") String groupId,
            @JsonProperty("faceScoreThreshold") Float faceScoreThreshold) {
        this.imgUrl = imgUrl;
        this.groupId = groupId;
        this.faceScoreThreshold = (faceScoreThreshold != null) ? faceScoreThreshold : 0.5f;
    }

    @Override
    public String toString() {
        return "DetectionRequest{" +
                "imgUrl='" + imgUrl + '\'' +
                ", groupId='" + groupId + '\'' +
                ", faceScoreThreshold=" + faceScoreThreshold +
                '}';
    }
}
