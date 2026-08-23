package com.yuqiangdede.tbir.config;

import com.yuqiangdede.common.config.YamlConfig;
import com.yuqiangdede.common.vector.VectorStoreMode;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class Constant {

    public static final String OPENCV_DLL_PATH;
    public static final String OPENCV_SO_PATH;
    public static final String IMG_ONNX;
    public static final String TEXT_ONNX;
    public static final String CLIP_TOKENIZER;
    public static final int VISION_IMAGE_SIZE;
    public static final String VISION_IMAGE_INPUT_NAME;
    public static final String VISION_TEXT_INPUT_IDS_NAME;
    public static final String VISION_TEXT_ATTENTION_MASK_NAME;
    public static final float[] VISION_IMAGE_MEAN;
    public static final float[] VISION_IMAGE_STD;
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
                config.get("vision-mind.tbir-cn.models.image", "/tbir/model-cn/metaclip2-mt5-m16-img-512.onnx"));
        TEXT_ONNX = resolvePath(resourceRoot,
                config.get("vision-mind.tbir-cn.models.text", "/tbir/model-cn/metaclip2-mt5-m16-text-512.onnx"));
        CLIP_TOKENIZER = resolvePath(resourceRoot,
                config.get("vision-mind.tbir-cn.models.tokenizer", "/tbir/clip-tokenizer-cn"));

        VISION_IMAGE_SIZE = config.getInt("vision-mind.tbir-cn.vision.image.size", 224);
        VISION_IMAGE_INPUT_NAME = config.get("vision-mind.tbir-cn.vision.image.input", "pixel_values");
        VISION_TEXT_INPUT_IDS_NAME = config.get("vision-mind.tbir-cn.vision.text.input-ids", "input_ids");
        VISION_TEXT_ATTENTION_MASK_NAME = config.get("vision-mind.tbir-cn.vision.text.attention-mask", "attention_mask");
        VISION_IMAGE_MEAN = parseFloatArray(config.getStringList("vision-mind.tbir-cn.vision.image.mean"),
                new float[]{0.48145466f, 0.4578275f, 0.40821073f});
        VISION_IMAGE_STD = parseFloatArray(config.getStringList("vision-mind.tbir-cn.vision.image.std"),
                new float[]{0.26862954f, 0.26130258f, 0.27577711f});

        LUCENE_PATH = resolvePath(resourceRoot,
                config.get("vision-mind.tbir-cn.vector-store.lucene-path", "/data/tbirIndexCn"));
        VECTOR_STORE_MODE = VectorStoreMode.fromProperty(
                config.get("vision-mind.tbir-cn.vector-store.mode", "memory"));
        ES_URIS = config.get("vision-mind.tbir-cn.vector-store.elasticsearch.uris", "http://127.0.0.1:9200");
        ES_USERNAME = trimToNull(config.get("vision-mind.tbir-cn.vector-store.elasticsearch.username", ""));
        ES_PASSWORD = trimToNull(config.get("vision-mind.tbir-cn.vector-store.elasticsearch.password", ""));
        ES_API_KEY = trimToNull(config.get("vision-mind.tbir-cn.vector-store.elasticsearch.api-key", ""));
        ES_TBIR_INDEX = config.get("vision-mind.tbir-cn.vector-store.elasticsearch.index", "vision_mind_tbir_cn");

        OPEN_DETECT = config.getBoolean("vision-mind.tbir-cn.detection.enabled", true);
        DETECT_TYPES = new LinkedHashSet<>(config.getStringList("vision-mind.tbir-cn.detection.types"));
        MIN_SIZE = config.getInt("vision-mind.tbir-cn.filter.min-size", 50);
        MAX_SIZE = config.getInt("vision-mind.tbir-cn.filter.max-size", 300);
        KEY_NUM = config.getInt("vision-mind.tbir-cn.key.expand-num", 5);
        AUGMENT_TYPES = new LinkedHashSet<>(config.getStringList("vision-mind.tbir-cn.augment-types"));
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

    private static float[] parseFloatArray(List<String> values, float[] defaultValue) {
        if (values.isEmpty()) {
            return defaultValue;
        }
        if (values.size() != defaultValue.length) {
            throw new IllegalStateException("vision image configuration must contain " + defaultValue.length + " values");
        }
        float[] result = new float[values.size()];
        for (int i = 0; i < values.size(); i++) {
            result[i] = Float.parseFloat(values.get(i));
        }
        return result;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
