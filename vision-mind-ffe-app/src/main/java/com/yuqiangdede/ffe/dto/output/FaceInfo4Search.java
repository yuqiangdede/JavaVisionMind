package com.yuqiangdede.ffe.dto.output;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class FaceInfo4Search {
    /**
     * 人脸ID
     **/
    public String id;

    /**
     * 人脸搜索的置信度
     **/
    public float confidence;
    public String imgUrl;

    public FaceInfo4Search(String id, String imgUrl, float confidence) {
        this.id = id;
        this.imgUrl = imgUrl;
        this.confidence = confidence;
    }

    @Override
    public String toString() {
        return "FaceInfo4Search{" +
                "id='" + id + '\'' +
                ", confidence=" + confidence +
                ", imgUrl='" + imgUrl + '\'' +
                '}';
    }
}
