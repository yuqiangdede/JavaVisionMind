package com.yuqiangdede.ffe.dto.input;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.Arrays;


@Setter
@Getter
public class Input4Save {

    private final String id;
    private final String imgUrl;

    /**
     * 人脸分组
     */
    private final String groupId;


    private final float[] embeds;

    @JsonCreator
    public Input4Save(
            @JsonProperty("imgUrl") String imgUrl,
            @JsonProperty("groupId") String groupId,
            @JsonProperty("id") String id, @JsonProperty("embeds") float[] embeds) {
        this.imgUrl = imgUrl;
        this.groupId = groupId;
        this.id = id;
        this.embeds = embeds;
    }

    @Override
    public String toString() {
        return "Input4Save{" +
                "id='" + id + '\'' +
                ", imgUrl='" + imgUrl + '\'' +
                ", groupId='" + groupId + '\'' +
                ", embeds=" + Arrays.toString(embeds) +
                '}';
    }
}
