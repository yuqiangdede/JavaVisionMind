package com.yuqiangdede.tbir.dto.output;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ImageSaveResult {

    /**
     * 系统生成或用户指定的图片 ID
     */
    private String imageId;

}
