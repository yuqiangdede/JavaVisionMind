package com.yuqiangdede.tbir.dto.input;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;


@Setter
@Getter
public class SearchImageRequest {

    private final String imgId;


    @JsonCreator
    public SearchImageRequest(@JsonProperty("imageId") String imgId) {
        this.imgId = imgId;
    }

    @Override
    public String toString() {
        return "SearchImageRequest{" +
                "imgId='" + imgId + '\'' +
                '}';
    }
}
