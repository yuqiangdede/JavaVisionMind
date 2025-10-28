package com.yuqiangdede.ocr.dto.input;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.yuqiangdede.common.dto.Point;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OcrDetectionRequest {

    /**
     * Detection level: light/lite or heavy; defaults to light when absent.
     */
    @JsonAlias({"level", "modelLevel"})
    private String detectionLevel;

    /**
     * Source image URL to analyse.
     */
    private String imgUrl;

    /**
     * Optional polygons where detections are allowed.
     */
    private ArrayList<ArrayList<Point>> detectionFrames;

    /**
     * Optional polygons where detections must be ignored.
     */
    private ArrayList<ArrayList<Point>> blockingFrames;

    @Override
    public String toString() {
        return "OcrDetectionRequest{" +
                "detectionLevel='" + detectionLevel + '\'' +
                ", imgUrl='" + imgUrl + '\'' +
                ", detectionFrames=" + detectionFrames +
                ", blockingFrames=" + blockingFrames +
                '}';
    }
}
