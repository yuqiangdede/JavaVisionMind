package com.yuqiangdede.ffe.dto.output;

import com.yuqiangdede.ffe.core.domain.FaceInfo;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class FaceInfo4Add {
    /**
     * 人脸ID
     **/
    public String id;
    /**
     * 人脸质量分数
     **/
    public float score;

    /**
     * 人脸旋转角度
     **/
    public float angle;
    /**
     * 当前图片的人脸向量信息
     **/
    public float[] embeds;
    public Integer age;
    public Integer gender;



    public FaceInfo4Add(FaceInfo faceInfo) {
        this.id = faceInfo.getId();
        this.score = faceInfo.getScore();
        this.angle = faceInfo.getAngle();
        this.embeds = faceInfo.getEmbedding().getEmbeds();
        this.age = faceInfo.getAttribute().getAge();
        this.gender = faceInfo.getAttribute().getGender();
    }

    @Override
    public String toString() {
        return "FaceInfo4Add{" +
                "id='" + id + '\'' +
                ", score=" + score +
                '}';
    }
}
