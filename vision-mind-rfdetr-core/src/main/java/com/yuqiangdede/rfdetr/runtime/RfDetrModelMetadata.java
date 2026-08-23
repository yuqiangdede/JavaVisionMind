package com.yuqiangdede.rfdetr.runtime;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
public class RfDetrModelMetadata {

    private String modelSha256;
    private String source;
    private String license;
    private TensorSpec input;
    private Map<String, TensorSpec> outputs = new LinkedHashMap<>();
    private Map<Integer, String> classNames = new LinkedHashMap<>();

    @Data
    public static class TensorSpec {
        private String name;
        private String type;
        private List<Long> shape;
    }
}
