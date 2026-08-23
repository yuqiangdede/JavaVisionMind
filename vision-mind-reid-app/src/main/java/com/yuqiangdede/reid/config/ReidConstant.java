package com.yuqiangdede.reid.config;

import com.yuqiangdede.common.config.YamlConfig;
import com.yuqiangdede.common.vector.VectorStoreMode;

public final class ReidConstant {

    public static final String LUCENE_PATH;
    public static final String ONNX_PATH;
    public static final VectorStoreMode VECTOR_STORE_MODE;

    public static final String ES_URIS;
    public static final String ES_USERNAME;
    public static final String ES_PASSWORD;
    public static final String ES_API_KEY;
    public static final String ES_REID_INDEX;

    static {
        YamlConfig config = YamlConfig.load(ReidConstant.class);
        String resourceRoot = resourceRoot();

        LUCENE_PATH = resolvePath(resourceRoot,
                config.get("vision-mind.reid.vector-store.lucene-path", "/data/reidIndex"));
        ONNX_PATH = resolvePath(resourceRoot,
                config.get("vision-mind.reid.model.onnx", "/reid/model/AGW_R50-ibn-bn.onnx"));
        VECTOR_STORE_MODE = VectorStoreMode.fromProperty(
                config.get("vision-mind.reid.vector-store.mode", "memory"));
        ES_URIS = config.get("vision-mind.reid.vector-store.elasticsearch.uris", "http://127.0.0.1:9200");
        ES_USERNAME = trimToNull(config.get("vision-mind.reid.vector-store.elasticsearch.username", ""));
        ES_PASSWORD = trimToNull(config.get("vision-mind.reid.vector-store.elasticsearch.password", ""));
        ES_API_KEY = trimToNull(config.get("vision-mind.reid.vector-store.elasticsearch.api-key", ""));
        ES_REID_INDEX = config.get("vision-mind.reid.vector-store.elasticsearch.index", "vision_mind_reid");
    }

    private ReidConstant() {
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
