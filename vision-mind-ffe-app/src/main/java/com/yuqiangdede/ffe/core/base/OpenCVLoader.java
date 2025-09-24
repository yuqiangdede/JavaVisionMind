package com.yuqiangdede.ffe.core.base;

import com.yuqiangdede.ffe.config.Constant;

public abstract class OpenCVLoader {

    //静态加载动态链接库
    static {
        // 加载opencv需要的动态库
        String osName = System.getProperty("os.name").toLowerCase();
        if (osName.contains("win")) {
            System.load(Constant.OPENCV_DLL_PATH);

        } else if (osName.contains("nix") || osName.contains("nux") || osName.contains("aix")) {
            System.load(Constant.OPENCV_SO_PATH);
        } else {
            throw new UnsupportedOperationException("Unsupported operating system: " + osName);
        }
    }

}
