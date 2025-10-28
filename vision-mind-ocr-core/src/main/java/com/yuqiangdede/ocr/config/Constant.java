package com.yuqiangdede.ocr.config;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

@Slf4j
public final class Constant {

    public static final String OPENCV_DLL_PATH;
    public static final String OPENCV_SO_PATH;

    public static final String ORC_DET_ONNX_PATH;
    public static final String ORC_DET2_ONNX_PATH;
    public static final String ORC_REC_ONNX_PATH;
    public static final String ORC_REC2_ONNX_PATH;
    public static final String ORC_CLS_ONNX_PATH;
    public static final String OCR_DICT_PATH;

    public static final double DETECT_RATIO;
    public static final double BLOCK_RATIO;
    public static final boolean USE_GPU;

    static {
        Properties properties = new Properties();
        try {
            loadProperties(properties, "native-defaults.properties", false);
            loadProperties(properties, "ocr-core.properties", true);

            String envPath = System.getenv("VISION_MIND_PATH");
            boolean skipNativeConfig = Boolean.parseBoolean(System.getProperty("vision-mind.skip-opencv", "false"));
            if (envPath == null || envPath.isBlank()) {
                if (!skipNativeConfig && !isTestEnvironment()) {
                    log.warn("VISION_MIND_PATH is not defined. Native resources will be resolved relative to the current directory.");
                }
                envPath = "";
            }

            OPENCV_DLL_PATH = envPath + properties.getProperty("opencv.dll.path");
            OPENCV_SO_PATH = envPath + properties.getProperty("opencv.so.path");

            ORC_DET_ONNX_PATH = envPath + properties.getProperty("orc.det.onnx.path");
            ORC_DET2_ONNX_PATH = envPath + properties.getProperty("orc.det2.onnx.path");
            ORC_REC_ONNX_PATH = envPath + properties.getProperty("orc.rec.onnx.path");
            ORC_REC2_ONNX_PATH = envPath + properties.getProperty("orc.rec2.onnx.path");
            ORC_CLS_ONNX_PATH = envPath + properties.getProperty("orc.cls.onnx.path");
            OCR_DICT_PATH = envPath + properties.getProperty("ocr.dict.path");

            DETECT_RATIO = Double.parseDouble(properties.getProperty("detect.ratio", "0.5"));
            BLOCK_RATIO = Double.parseDouble(properties.getProperty("block.ratio", "0.5"));
            USE_GPU = Boolean.parseBoolean(properties.getProperty("use.gpu", "false"));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load OCR configuration", e);
        }
    }

    private Constant() {
    }

    private static void loadProperties(Properties target, String resourceName, boolean required) throws IOException {
        try (InputStream stream = Constant.class.getClassLoader().getResourceAsStream(resourceName)) {
            if (stream == null) {
                if (required) {
                    throw new IOException(resourceName + " not found");
                }
                return;
            }
            target.load(stream);
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
