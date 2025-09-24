package com.yuqiangdede.ffe.core.domain;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 图片人脸信息
 */
@Getter
@Setter
public class FaceImage implements Serializable {

    /**
     * 图像数据
     **/
    public String imageBase64;
    /**
     * 人脸解析数据
     **/
    public List<FaceInfo> faceInfos;

    /**
     * 构建函数
     *
     * @param imageBase64 图像数据
     * @param faceInfos   人脸解析数据
     */
    private FaceImage(String imageBase64, List<FaceInfo> faceInfos) {
        this.imageBase64 = imageBase64;
        this.faceInfos = faceInfos;
    }

    @Override
    public String toString() {
        return "FaceImage{" +
                "faceInfos=" + faceInfos.size() +
                '}';
    }

    /**
     * 构建对象
     *
     * @param imageBase64 图像数据
     * @param faceInfos   人脸解析数据
     */
    public static FaceImage build(String imageBase64, List<FaceInfo> faceInfos) {
        if (faceInfos == null) {
            faceInfos = new ArrayList<>();
        }
        return new FaceImage(imageBase64, faceInfos);
    }

//    /**
//     * 获取图像数据
//     */
//    public ImageMat imageMat() {
//        return ImageMat.fromBase64(this.imageBase64);
//    }

    /**
     * 获取人脸解析数据
     */
    public List<FaceInfo> faceInfos() {
        return this.faceInfos;
    }
}
