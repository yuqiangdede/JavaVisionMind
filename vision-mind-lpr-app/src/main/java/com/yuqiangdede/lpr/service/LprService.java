package com.yuqiangdede.lpr.service;

import ai.onnxruntime.OrtException;
import com.yuqiangdede.common.dto.Point;
import com.yuqiangdede.common.dto.output.Box;
import com.yuqiangdede.common.util.ImageUtil;
import com.yuqiangdede.lpr.config.LprProperties;
import com.yuqiangdede.lpr.controller.dto.PlateRecognitionResult;
import com.yuqiangdede.ocr.dto.input.OcrDetectionRequest;
import com.yuqiangdede.ocr.dto.output.OcrDetectionResult;
import com.yuqiangdede.ocr.service.OcrService;
import com.yuqiangdede.yolo.dto.input.DetectionRequestWithArea;
import com.yuqiangdede.yolo.service.ImgAnalysisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class LprService {

    private final ImgAnalysisService imgAnalysisService;
    private final LprOnnxRecognizer recognizer;
    private final LprProperties properties;
    private final OcrService ocrService;

    public AnalysisResult analyze(DetectionRequestWithArea request) throws IOException, OrtException {
        BufferedImage original = ImageUtil.urlToImage(request.getImgUrl());
        List<Box> boxes = imgAnalysisService.detectLP(request);
        if (boxes.isEmpty()) {
            return new AnalysisResult(original, Collections.emptyList(), Collections.emptyList());
        }
        List<PlateRecognitionResult> results = new ArrayList<>();
        List<BufferedImage> plateImages = new ArrayList<>();
        for (Box box : boxes) {
            Optional<BufferedImage> normalized = normalizePlate(original, box);
            if (normalized.isEmpty()) {
                log.warn("车牌区域 {} 无法裁剪，跳过", box);
                continue;
            }
            BufferedImage plate = normalized.get();
            String text = recognizer.recognize(plate);
            results.add(new PlateRecognitionResult(box, text));
            plateImages.add(plate);
        }
        return new AnalysisResult(original, results, plateImages);
    }

    public AnalysisResult analyzeWithOcr(DetectionRequestWithArea request) throws IOException, OrtException {
        BufferedImage original = ImageUtil.urlToImage(request.getImgUrl());
        List<Box> boxes = imgAnalysisService.detectLP(request);
        if (boxes.isEmpty()) {
            return new AnalysisResult(original, Collections.emptyList(), Collections.emptyList());
        }

        OcrDetectionRequest ocrRequest = new OcrDetectionRequest();
        ocrRequest.setImgUrl(request.getImgUrl());
        List<OcrDetectionResult> detections = ocrService.detect(ocrRequest);

        List<PlateRecognitionResult> results = new ArrayList<>();
        List<BufferedImage> plateImages = new ArrayList<>();
        for (Box box : boxes) {
            Optional<BufferedImage> normalized = normalizePlate(original, box);
            normalized.ifPresent(plateImages::add);
            String text = selectOcrText(box, detections).map(OcrDetectionResult::getText).orElse("");
            results.add(new PlateRecognitionResult(box, text));
        }
        return new AnalysisResult(original, results, plateImages);
    }

    public BufferedImage overlay(AnalysisResult analysis) {
        BufferedImage baseImage = analysis.original();
        if (baseImage == null) {
            return new BufferedImage(properties.getModelInputWidth(), properties.getModelInputHeight(), BufferedImage.TYPE_3BYTE_BGR);
        }
        BufferedImage canvas = new BufferedImage(baseImage.getWidth(), baseImage.getHeight(), BufferedImage.TYPE_3BYTE_BGR);
        Graphics2D g = canvas.createGraphics();
        try {
            g.drawImage(baseImage, 0, 0, null);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setStroke(new BasicStroke(2f));
            g.setFont(new Font("SansSerif", Font.BOLD, 20));
            for (PlateRecognitionResult result : analysis.results()) {
                Box box = result.getBox();
                int x1 = Math.round(Math.min(box.getX1(), box.getX2()));
                int y1 = Math.round(Math.min(box.getY1(), box.getY2()));
                int x2 = Math.round(Math.max(box.getX1(), box.getX2()));
                int y2 = Math.round(Math.max(box.getY1(), box.getY2()));

                g.setColor(new Color(255, 215, 0));
                g.drawRect(x1, y1, Math.max(1, x2 - x1), Math.max(1, y2 - y1));
                if (result.getPlate() != null && !result.getPlate().isBlank()) {
                    String text = result.getPlate();
                    int textWidth = g.getFontMetrics().stringWidth(text) + 12;
                    int textHeight = g.getFontMetrics().getHeight();
                    int bgX = x1 + 2;
                    int bgY = Math.max(0, y1 - textHeight - 6);
                    g.setColor(new Color(30, 60, 200));
                    g.fillRect(bgX, bgY, textWidth, textHeight + 4);
                    g.setColor(Color.WHITE);
                    g.drawString(text, bgX + 6, bgY + textHeight - 6);
                }
            }
        } finally {
            g.dispose();
        }
        return canvas;
    }

    private Optional<BufferedImage> normalizePlate(BufferedImage image, Box box) {
        int width = image.getWidth();
        int height = image.getHeight();
        int left = Math.round(Math.max(0, Math.min(box.getX1(), box.getX2())));
        int top = Math.round(Math.max(0, Math.min(box.getY1(), box.getY2())) - 2);
        int right = Math.round(Math.min(width, Math.max(box.getX1(), box.getX2())));
        int bottom = Math.round(Math.min(height, Math.max(box.getY1(), box.getY2())) + 2);
        top = Math.max(0, top);
        bottom = Math.min(height, bottom);

        int w = right - left;
        int h = bottom - top;
        if (w <= 1 || h <= 1) {
            return Optional.empty();
        }

        BufferedImage cropped = new BufferedImage(w, h, BufferedImage.TYPE_3BYTE_BGR);
        Graphics2D croppedGraphics = cropped.createGraphics();
        try {
            croppedGraphics.drawImage(image, 0, 0, w, h, left, top, right, bottom, null);
        } finally {
            croppedGraphics.dispose();
        }

        int targetW = properties.getModelInputWidth();
        int targetH = properties.getModelInputHeight();
        BufferedImage scaled = new BufferedImage(targetW, targetH, BufferedImage.TYPE_3BYTE_BGR);
        Graphics2D g = scaled.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(cropped, 0, 0, targetW, targetH, 0, 0, w, h, null);
        } finally {
            g.dispose();
        }
        return Optional.of(scaled);
    }

    private Optional<OcrDetectionResult> selectOcrText(Box box, List<OcrDetectionResult> detections) {
        if (detections == null || detections.isEmpty()) {
            return Optional.empty();
        }
        double bestScore = Double.NEGATIVE_INFINITY;
        OcrDetectionResult best = null;
        for (OcrDetectionResult detection : detections) {
            double overlap = computeIoU(box, detection);
            if (overlap <= 0) {
                continue;
            }
            double score = detection.getConfidence() * overlap;
            if (score > bestScore) {
                bestScore = score;
                best = detection;
            }
        }
        if (best != null) {
            return Optional.of(best);
        }
        // fallback: choose detection whose centroid lies inside box
        for (OcrDetectionResult detection : detections) {
            if (containsCenter(box, detection)) {
                return Optional.of(detection);
            }
        }
        return Optional.empty();
    }

    private boolean containsCenter(Box box, OcrDetectionResult detection) {
        List<Point> polygon = detection.getPolygon();
        if (polygon == null || polygon.isEmpty()) {
            return false;
        }
        double[] bounds = polygonBounds(polygon);
        double cx = (bounds[0] + bounds[2]) / 2.0;
        double cy = (bounds[1] + bounds[3]) / 2.0;
        double x1 = Math.min(box.getX1(), box.getX2());
        double y1 = Math.min(box.getY1(), box.getY2());
        double x2 = Math.max(box.getX1(), box.getX2());
        double y2 = Math.max(box.getY1(), box.getY2());
        return cx >= x1 && cx <= x2 && cy >= y1 && cy <= y2;
    }

    private double computeIoU(Box box, OcrDetectionResult detection) {
        List<Point> polygon = detection.getPolygon();
        if (polygon == null || polygon.size() < 3) {
            return 0.0;
        }
        double[] bounds = polygonBounds(polygon);
        double plateLeft = Math.min(box.getX1(), box.getX2());
        double plateTop = Math.min(box.getY1(), box.getY2());
        double plateRight = Math.max(box.getX1(), box.getX2());
        double plateBottom = Math.max(box.getY1(), box.getY2());

        double detLeft = bounds[0];
        double detTop = bounds[1];
        double detRight = bounds[2];
        double detBottom = bounds[3];

        double interLeft = Math.max(plateLeft, detLeft);
        double interTop = Math.max(plateTop, detTop);
        double interRight = Math.min(plateRight, detRight);
        double interBottom = Math.min(plateBottom, detBottom);

        double interWidth = interRight - interLeft;
        double interHeight = interBottom - interTop;
        if (interWidth <= 0 || interHeight <= 0) {
            return 0.0;
        }
        double interArea = interWidth * interHeight;
        double plateArea = Math.max(1.0, (plateRight - plateLeft) * (plateBottom - plateTop));
        double detArea = Math.max(1.0, (detRight - detLeft) * (detBottom - detTop));
        double union = plateArea + detArea - interArea;
        if (union <= 0) {
            return 0.0;
        }
        return interArea / union;
    }

    private double[] polygonBounds(List<Point> polygon) {
        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxX = Double.MIN_VALUE;
        double maxY = Double.MIN_VALUE;
        for (Point point : polygon) {
            if (point == null || point.getX() == null || point.getY() == null) {
                continue;
            }
            double x = point.getX();
            double y = point.getY();
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
        }
        if (!Double.isFinite(minX) || !Double.isFinite(maxX)) {
            return new double[]{0, 0, 0, 0};
        }
        return new double[]{minX, minY, maxX, maxY};
    }

    public record AnalysisResult(BufferedImage original, List<PlateRecognitionResult> results, List<BufferedImage> normalizedPlates) {
        public AnalysisResult {
            results = results == null ? List.of() : List.copyOf(results);
            normalizedPlates = normalizedPlates == null ? List.of() : List.copyOf(normalizedPlates);
        }

        public BufferedImage original() {
            return original;
        }

        public List<BufferedImage> normalizedPlates() {
            return normalizedPlates;
        }
    }
}
