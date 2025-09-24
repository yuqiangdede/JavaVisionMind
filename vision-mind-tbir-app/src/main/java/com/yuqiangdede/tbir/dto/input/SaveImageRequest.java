package com.yuqiangdede.tbir.dto.input;

import com.yuqiangdede.yolo.dto.input.DetectionRequestWithArea;
import lombok.*;

import java.util.Map;


@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SaveImageRequest extends DetectionRequestWithArea {

    /**
     * 可选提供自定义图片ID
     */
    private String imgId;
    /**
     * 监控点IndexCode
     */
    private String cameraId;
    /**
     * 图片分组ID，用于区分不同业务场景的图片
     */
    private String groupId;
    /**
     * 可扩展性字段，比如传入业务属性，不支持作为检索条件
     */
    private Map<String, String> meta;

    @Override
    public String toString() {
        return "SaveImageRequest{" +
                "imgId='" + imgId + '\'' +
                ", cameraId='" + cameraId + '\'' +
                ", groupId='" + groupId + '\'' +
                ", imgUrl=" + super.getImgUrl() +
                ", meta=" + meta +
                ", threshold=" + super.getThreshold() +
                ", types=" + super.getTypes() +
                "} ";
    }
}
