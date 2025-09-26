package com.yuqiangdede.ffe.core.base;

import com.yuqiangdede.ffe.config.Constant;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class OpenCVLoader {

    static {
        boolean skipProperty = Boolean.parseBoolean(System.getProperty("vision-mind.skip-opencv", "false"));
        boolean testEnv = isTestEnvironment();
        if (skipProperty || testEnv) {
            log.debug("Skipping OpenCV native load. skipProperty={}, testEnv={}", skipProperty, testEnv);
        } else {
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

    private static boolean isTestEnvironment() {
        try {
            Class.forName("org.junit.jupiter.api.Test");
            return true;
        } catch (ClassNotFoundException ex) {
            return false;
        }
    }
}
