package com.yuqiangdede.ocr.config;

import com.yuqiangdede.common.config.YamlConfig;

public final class Constant {

    public static final String OPENCV_DLL_PATH;
    public static final String OPENCV_SO_PATH;

    public static final String ORC_DET_ONNX_PATH;
    public static final String ORC_DET2_ONNX_PATH;
    public static final String ORC_REC_ONNX_PATH;
    public static final String ORC_REC2_ONNX_PATH;
    public static final String ORC_CLS_ONNX_PATH;
    public static final String OCR_DICT_PATH;

    public static final boolean USE_GPU;

    static {
        YamlConfig config = YamlConfig.load(Constant.class);
        String envPath = resourceRoot();

        OPENCV_DLL_PATH = resolvePath(envPath,
                config.get("vision-mind.native.dll-path", "/lib/opencv/opencv_java490.dll"));
        OPENCV_SO_PATH = resolvePath(envPath,
                config.get("vision-mind.native.so-path", "/lib/opencv/libopencv_java4100.so"));

        ORC_DET_ONNX_PATH = resolvePath(envPath,
                config.get("vision-mind.ocr.models.det", "/ocr/model/det.onnx"));
        ORC_DET2_ONNX_PATH = resolvePath(envPath,
                config.get("vision-mind.ocr.models.det2", "/ocr/model/det2.onnx"));
        ORC_REC_ONNX_PATH = resolvePath(envPath,
                config.get("vision-mind.ocr.models.rec", "/ocr/model/rec.onnx"));
        ORC_REC2_ONNX_PATH = resolvePath(envPath,
                config.get("vision-mind.ocr.models.rec2", "/ocr/model/rec2.onnx"));
        ORC_CLS_ONNX_PATH = resolvePath(envPath,
                config.get("vision-mind.ocr.models.cls", "/ocr/model/cls.onnx"));
        OCR_DICT_PATH = resolvePath(envPath,
                config.get("vision-mind.ocr.dict-path", "/ocr/dict.txt"));
        USE_GPU = config.getBoolean("vision-mind.native.use-gpu", false);
    }

    private Constant() {
    }

    private static String resourceRoot() {
        String envPath = System.getenv("VISION_MIND_PATH");
        return envPath == null ? "" : envPath;
    }

    private static String resolvePath(String resourceRoot, String configuredPath) {
        return resourceRoot + configuredPath;
    }
}
