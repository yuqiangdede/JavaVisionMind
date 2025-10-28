package com.yuqiangdede.ocr.dto.output;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.yuqiangdede.common.dto.Point;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OcrDetectionResult {

    /**
     * Quadrilateral describing the detected text area.
     */
    private List<Point> polygon;

    /**
     * Recognised text content.
     */
    private String text;

    /**
     * Average confidence for the recognition output.
     */
    private double confidence;
}
