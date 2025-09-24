package com.yuqiangdede.reid.output;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

@Setter
@Getter
public class Feature implements Serializable {

    private String uuid;
    private float[] embeds;


    public Feature(String uuid, float[] embeds) {
        this.uuid = uuid;
        this.embeds = embeds;
    }
}