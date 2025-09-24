package com.yuqiangdede.tbir.dto.input;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;


@Setter
@Getter
public class DeleteImageRequest {


    /**
     * 人脸id,用于删除
     */
    private final String imgId;


    @JsonCreator
    public DeleteImageRequest(@JsonProperty("imageId") String imgId) {
        this.imgId = imgId;
    }

    @Override
    public String toString() {
        return "DeleteImageRequest{" +
                "imgId='" + imgId + '\'' +
                '}';
    }
}
