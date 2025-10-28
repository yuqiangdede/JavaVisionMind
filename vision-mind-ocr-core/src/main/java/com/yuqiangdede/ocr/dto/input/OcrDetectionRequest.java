package com.yuqiangdede.ocr.dto.input;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OcrDetectionRequest {

    /**
     * Detection level: lite (default) or ex for the heavier pipeline.
     */
    @JsonAlias({"level", "modelLevel"})
    private String detectionLevel;

    /**
     * Detection plan: sr (default，SemanticReconstruction) or patch
     */
    private String plan;
    /**
     * Source image URL to analyse.
     */
    private String imgUrl;

    @Override
    public String toString() {
        return "OcrDetectionRequest{" +
                "detectionLevel='" + detectionLevel + '\'' +
                ", plan='" + plan + '\'' +
                ", imgUrl='" + imgUrl + '\'' +
                '}';
    }
}
