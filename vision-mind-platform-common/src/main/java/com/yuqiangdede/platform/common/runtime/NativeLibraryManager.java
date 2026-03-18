package com.yuqiangdede.platform.common.runtime;

import com.yuqiangdede.platform.common.config.VisionMindProperties;
import com.yuqiangdede.platform.common.util.RuntimeEnvironmentCompat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NativeLibraryManager {

    private static final Logger log = LoggerFactory.getLogger(NativeLibraryManager.class);

    private final VisionMindProperties properties;

    public NativeLibraryManager(VisionMindProperties properties) {
        this.properties = properties;
    }

    public void loadOpenCv(String windowsPath, String linuxPath) {
        boolean skip = properties.getNativeLoad().isSkipOpenCv() || RuntimeEnvironmentCompat.shouldSkipNativeLoad();
        if (skip) {
            log.info("skip OpenCV native load due to configuration or test runtime");
            return;
        }
        String osName = System.getProperty("os.name", "").toLowerCase();
        try {
            if (osName.contains("win")) {
                System.load(windowsPath);
            } else if (osName.contains("nix") || osName.contains("nux") || osName.contains("aix")) {
                System.load(linuxPath);
            } else {
                throw new UnsupportedOperationException("unsupported os: " + osName);
            }
        } catch (UnsatisfiedLinkError ex) {
            throw new IllegalStateException("failed to load opencv native library", ex);
        }
    }
}
