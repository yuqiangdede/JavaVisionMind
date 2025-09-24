package com.yuqiangdede.reid.output;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class Human {

    private String humanId;
    private String imageId;
    private String imgUrl;
    private float score;
    private String cameraId;
    private String type;

    public Human(String humanId, String imageId, String imgUrl, float score, String cameraId, String type) {
        this.humanId = humanId;
        this.imageId = imageId;
        this.imgUrl = imgUrl;
        this.score = score;
        this.cameraId = cameraId;
        this.type = type;
    }

    @Override
    public String toString() {
        return "Human{" +
                "humanId='" + humanId + '\'' +
                ", imageId='" + imageId + '\'' +
                ", imgUrl='" + imgUrl + '\'' +
                ", score=" + score +
                ", cameraId='" + cameraId + '\'' +
                ", type='" + type + '\'' +
                '}';
    }
}
