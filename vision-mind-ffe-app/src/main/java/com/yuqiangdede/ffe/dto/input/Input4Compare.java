package com.yuqiangdede.ffe.dto.input;

import lombok.Getter;
import lombok.Setter;


@Setter
@Getter
public class Input4Compare {

    /**
     * 图片url
     */
    private final String imgUrl;
    /**
     * 图片url2,用于两张图片比对使用
     */
    private final String imgUrl2;


    public Input4Compare(String imgUrl, String imgUrl2) {
        this.imgUrl = imgUrl;
        this.imgUrl2 = imgUrl2;
    }

    @Override
    public String toString() {
        return "Input4Compare{" +
                "imgUrl='" + imgUrl + '\'' +
                ", imgUrl2='" + imgUrl2 + '\'' +
                '}';
    }
}
