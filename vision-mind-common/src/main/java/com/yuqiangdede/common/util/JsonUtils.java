package com.yuqiangdede.common.util;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

public class JsonUtils {
    public static final ObjectMapper objectMapper = new ObjectMapper();
    public static final ObjectMapper objectNoNullMapper = new ObjectMapper();

    static {
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        objectNoNullMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        objectNoNullMapper.setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL);
    }

    public static String object2Json(Object obj) throws JsonProcessingException {
        if (obj == null) {
            return "";
        }
        return objectMapper.writeValueAsString(obj);
    }

    public static String map2Json(Map<String, String> meta) {
        if (meta == null || meta.isEmpty()) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(meta);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("map2Json failed", e);
        }
    }

    @SuppressWarnings("unchecked")
    public static Map<String, String> json2Map(String json) {
        if (json == null || json.trim().isEmpty()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("json2Map failed", e);
        }
    }

}
