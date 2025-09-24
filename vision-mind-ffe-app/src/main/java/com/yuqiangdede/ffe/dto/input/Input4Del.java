package com.yuqiangdede.ffe.dto.input;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;


@Setter
@Getter
public class Input4Del {


    /**
     * 人脸id,用于删除
     */
    private final String id;


    @JsonCreator
    public Input4Del(@JsonProperty("id") String id) {
        this.id = id;
    }

    @Override
    public String toString() {
        return "Input4Del{" +
                "id='" + id + '\'' +
                '}';
    }
}
