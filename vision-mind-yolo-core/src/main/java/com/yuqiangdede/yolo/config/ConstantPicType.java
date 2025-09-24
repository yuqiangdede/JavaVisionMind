package com.yuqiangdede.yolo.config;

import lombok.Getter;

@Getter
public enum ConstantPicType {
    JPG("jpg"),
    JPEG("jpeg"),
    PNG("png"),
    //    GIF("gif"),
    BMP("bmp");
    //    TIFF("tiff"),
    //    WEBP("webp");

    // 获取文件扩展名
    private final String extension;

    // 构造函数
    ConstantPicType(String extension) {
        this.extension = extension;
    }

    @Override
    public String toString() {
        return this.extension;
    }


    public static ConstantPicType formatExtension(String input) {
        if (input == null || input.isEmpty()) {
            return JPG;
        }

        for (ConstantPicType format : ConstantPicType.values()) {
            if (input.toLowerCase().contains(format.getExtension().toLowerCase())) {
                return format;
            }
        }

        return JPG;
    }
}
