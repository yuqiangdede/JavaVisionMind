package com.yuqiangdede.asr.dto.output;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AsrHealthResponse {

    private boolean ready;
    private String modelDir;
    private String configDir;
    private String uploadDir;
    private String runtimeJavaJar;
    private String runtimeNativeJar;
    private String message;
}
