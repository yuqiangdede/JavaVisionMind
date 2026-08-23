package com.yuqiangdede.ffe.config;

import com.yuqiangdede.common.config.YamlConfig;
import com.yuqiangdede.common.vector.VectorStoreMode;

public final class Constant {

    public static final String OPENCV_DLL_PATH;
    public static final String OPENCV_SO_PATH;

    public static final String MODEL_SCRFD_PATH;
    public static final String MODEL_COORD_PATH;
    public static final String MODEL_ARC_PATH;
    public static final String MODEL_ARR_PATH;
    public static final String LUCENE_PATH;
    public static final VectorStoreMode VECTOR_STORE_MODE;

    public static final String ES_URIS;
    public static final String ES_USERNAME;
    public static final String ES_PASSWORD;
    public static final String ES_API_KEY;
    public static final String ES_FACE_INDEX;

    static {
        YamlConfig config = YamlConfig.load(Constant.class);
        String resourceRoot = resourceRoot();

        OPENCV_DLL_PATH = resolvePath(resourceRoot,
                config.get("vision-mind.native.dll-path", "/lib/opencv/opencv_java490.dll"));
        OPENCV_SO_PATH = resolvePath(resourceRoot,
                config.get("vision-mind.native.so-path", "/lib/opencv/libopencv_java4100.so"));
        MODEL_SCRFD_PATH = resolvePath(resourceRoot,
                config.get("vision-mind.ffe.models.scrfd", "/ffe/model/detection_face_scrfd/scrfd_500m_bnkps.onnx"));
        MODEL_COORD_PATH = resolvePath(resourceRoot,
                config.get("vision-mind.ffe.models.coord", "/ffe/model/keypoint_coordinate/coordinate_106_mobilenet_05.onnx"));
        MODEL_ARC_PATH = resolvePath(resourceRoot,
                config.get("vision-mind.ffe.models.arc", "/ffe/model/recognition_face_arc/glint360k_cosface_r18_fp16_0.1.onnx"));
        MODEL_ARR_PATH = resolvePath(resourceRoot,
                config.get("vision-mind.ffe.models.arr", "/ffe/model/attribute_gender_age/insight_gender_age.onnx"));
        LUCENE_PATH = resolvePath(resourceRoot,
                config.get("vision-mind.ffe.vector-store.lucene-path", "/data/faceIndex"));

        VECTOR_STORE_MODE = VectorStoreMode.fromProperty(
                config.get("vision-mind.ffe.vector-store.mode", "memory"));
        ES_URIS = config.get("vision-mind.ffe.vector-store.elasticsearch.uris", "http://127.0.0.1:9200");
        ES_USERNAME = trimToNull(config.get("vision-mind.ffe.vector-store.elasticsearch.username", ""));
        ES_PASSWORD = trimToNull(config.get("vision-mind.ffe.vector-store.elasticsearch.password", ""));
        ES_API_KEY = trimToNull(config.get("vision-mind.ffe.vector-store.elasticsearch.api-key", ""));
        ES_FACE_INDEX = config.get("vision-mind.ffe.vector-store.elasticsearch.index", "vision_mind_face");
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

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
