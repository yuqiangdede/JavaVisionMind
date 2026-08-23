package com.yuqiangdede.tbir.config;

import com.yuqiangdede.common.config.YamlConfig;
import com.yuqiangdede.common.vector.VectorStoreMode;

import java.util.LinkedHashSet;
import java.util.Set;

public final class Constant {

    public static final String OPENCV_DLL_PATH;
    public static final String OPENCV_SO_PATH;
    public static final String IMG_ONNX;
    public static final String TEXT_ONNX;
    public static final String CLIP_TOKENIZER;
    public static final String LUCENE_PATH;
    public static final VectorStoreMode VECTOR_STORE_MODE;
    public static final String ES_URIS;
    public static final String ES_USERNAME;
    public static final String ES_PASSWORD;
    public static final String ES_API_KEY;
    public static final String ES_TBIR_INDEX;
    public static final Boolean OPEN_DETECT;
    public static final int MIN_SIZE;
    public static final int MAX_SIZE;
    public static final int KEY_NUM;
    public static final Set<String> AUGMENT_TYPES;
    public static final Set<String> DETECT_TYPES;

    static {
        YamlConfig config = YamlConfig.load(Constant.class);
        String resourceRoot = resourceRoot();

        OPENCV_DLL_PATH = resolvePath(resourceRoot,
                config.get("vision-mind.native.dll-path", "/lib/opencv/opencv_java490.dll"));
        OPENCV_SO_PATH = resolvePath(resourceRoot,
                config.get("vision-mind.native.so-path", "/lib/opencv/libopencv_java4100.so"));
        IMG_ONNX = resolvePath(resourceRoot,
                config.get("vision-mind.tbir.models.image", "/tbir/model/clip-vit-b32-img.onnx"));
        TEXT_ONNX = resolvePath(resourceRoot,
                config.get("vision-mind.tbir.models.text", "/tbir/model/clip-vit-b32-text.onnx"));
        CLIP_TOKENIZER = resolvePath(resourceRoot,
                config.get("vision-mind.tbir.models.tokenizer", "/tbir/clip-tokenizer"));
        LUCENE_PATH = resolvePath(resourceRoot,
                config.get("vision-mind.tbir.vector-store.lucene-path", "/data/tbirIndex"));
        VECTOR_STORE_MODE = VectorStoreMode.fromProperty(
                config.get("vision-mind.tbir.vector-store.mode", "memory"));
        ES_URIS = config.get("vision-mind.tbir.vector-store.elasticsearch.uris", "http://127.0.0.1:9200");
        ES_USERNAME = trimToNull(config.get("vision-mind.tbir.vector-store.elasticsearch.username", ""));
        ES_PASSWORD = trimToNull(config.get("vision-mind.tbir.vector-store.elasticsearch.password", ""));
        ES_API_KEY = trimToNull(config.get("vision-mind.tbir.vector-store.elasticsearch.api-key", ""));
        ES_TBIR_INDEX = config.get("vision-mind.tbir.vector-store.elasticsearch.index", "vision_mind_tbir");

        OPEN_DETECT = config.getBoolean("vision-mind.tbir.detection.enabled", true);
        DETECT_TYPES = new LinkedHashSet<>(config.getStringList("vision-mind.tbir.detection.types"));
        MIN_SIZE = config.getInt("vision-mind.tbir.filter.min-size", 50);
        MAX_SIZE = config.getInt("vision-mind.tbir.filter.max-size", 300);
        KEY_NUM = config.getInt("vision-mind.tbir.key.expand-num", 5);
        AUGMENT_TYPES = new LinkedHashSet<>(config.getStringList("vision-mind.tbir.augment-types"));
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
