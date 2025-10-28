package com.yuqiangdede.ocr.service;

import ai.onnxruntime.OrtException;
import com.yuqiangdede.common.dto.Point;
import com.yuqiangdede.common.util.ImageUtil;
import com.yuqiangdede.ocr.config.Constant;
import com.yuqiangdede.ocr.dto.input.OcrDetectionRequest;
import com.yuqiangdede.ocr.dto.output.OcrDetectionResult;
import com.yuqiangdede.ocr.runtime.PaddleOcrEngine;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.PrecisionModel;
import org.opencv.core.Mat;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@Slf4j
public class OcrService {

    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory(new PrecisionModel(), 0);

    private final PaddleOcrEngine lightEngine;
    private final PaddleOcrEngine heavyEngine;

    static {
        boolean skipProperty = Boolean.parseBoolean(System.getProperty("vision-mind.skip-opencv", "false"));
        boolean testEnv = isTestEnvironment();
        boolean skipLoad = skipProperty || testEnv;
        log.info("OpenCV native load check - skipProperty={}, testEnv={}", skipProperty, testEnv);
        if (skipLoad) {
            log.warn("Skipping OpenCV native library load because tests or configuration requested it");
        } else {
            String osName = System.getProperty("os.name").toLowerCase(Locale.ROOT);
            try {
                if (osName.contains("win")) {
                    System.load(Constant.OPENCV_DLL_PATH);
                } else if (osName.contains("nix") || osName.contains("nux") || osName.contains("aix")) {
                    System.load(Constant.OPENCV_SO_PATH);
                } else {
                    throw new UnsupportedOperationException("Unsupported operating system: " + osName);
                }
            } catch (UnsatisfiedLinkError e) {
                throw new IllegalStateException("Failed to load OpenCV native library", e);
            }
        }
    }

    public OcrService() {
        try {
            lightEngine = new PaddleOcrEngine(
                    Path.of(Constant.ORC_DET_ONNX_PATH),
                    Path.of(Constant.ORC_REC_ONNX_PATH),
                    Path.of(Constant.ORC_CLS_ONNX_PATH),
                    Path.of(Constant.OCR_DICT_PATH),
                    true
            );
            heavyEngine = new PaddleOcrEngine(
                    Path.of(Constant.ORC_DET2_ONNX_PATH),
                    Path.of(Constant.ORC_REC2_ONNX_PATH),
                    Path.of(Constant.ORC_CLS_ONNX_PATH),
                    Path.of(Constant.OCR_DICT_PATH),
                    true
            );
        } catch (IOException | OrtException e) {
            throw new IllegalStateException("Failed to initialise OCR engines", e);
        }
    }

    public List<OcrDetectionResult> detect(OcrDetectionRequest request) throws IOException, OrtException {
        InferenceResult inferenceResult = runInference(request);
        return inferenceResult.detections();
    }

    public BufferedImage detectWithOverlay(OcrDetectionRequest request) throws IOException, OrtException {
        InferenceResult inferenceResult = runInference(request);
        BufferedImage image = inferenceResult.image();
        drawDetections(image, inferenceResult.detections());
        ImageUtil.drawImageWithFrames(image, request.getDetectionFrames(), Color.BLUE);
        ImageUtil.drawImageWithFrames(image, request.getBlockingFrames(), Color.DARK_GRAY);
        return image;
    }

    public byte[] detectWithOverlayBytes(OcrDetectionRequest request) throws IOException, OrtException {
        BufferedImage image = detectWithOverlay(request);
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            javax.imageio.ImageIO.write(image, "jpg", baos);
            return baos.toByteArray();
        }
    }

    private InferenceResult runInference(OcrDetectionRequest request) throws IOException, OrtException {
        if (request == null) {
            throw new IllegalArgumentException("request must not be null");
        }
        if (ObjectUtils.isEmpty(request.getImgUrl())) {
            throw new IllegalArgumentException("imgUrl is null or empty");
        }

        long start = System.currentTimeMillis();
        Mat mat = ImageUtil.urlToMat(request.getImgUrl());
        BufferedImage image = ImageUtil.matToBufferedImage(mat);

        try {
            PaddleOcrEngine engine = selectEngine(request.getDetectionLevel());
            List<PaddleOcrEngine.OcrResult> rawResults = engine.ocr(mat);
            List<OcrDetectionResult> filtered = filterDetections(
                    rawResults,
                    request.getDetectionFrames(),
                    request.getBlockingFrames());
            log.info("OCR inference completed: url={}, level={}, totalDetections={}, filteredDetections={}, cost={}ms",
                    request.getImgUrl(),
                    normaliseDetectionLevel(request.getDetectionLevel()),
                    rawResults.size(),
                    filtered.size(),
                    System.currentTimeMillis() - start);
            return new InferenceResult(image, filtered);
        } finally {
            mat.release();
        }
    }

    private List<OcrDetectionResult> filterDetections(List<PaddleOcrEngine.OcrResult> raw,
                                                      ArrayList<ArrayList<Point>> detectionFrames,
                                                      ArrayList<ArrayList<Point>> blockingFrames) {
        if (raw.isEmpty()) {
            return Collections.emptyList();
        }

        List<Polygon> includePolygons = toPolygons(detectionFrames);
        List<Polygon> blockPolygons = toPolygons(blockingFrames);

        List<OcrDetectionResult> results = new ArrayList<>();
        for (PaddleOcrEngine.OcrResult result : raw) {
            Polygon detectionPolygon = polygonFromBox(result.getBox());
            if (detectionPolygon == null || detectionPolygon.getArea() <= 0) {
                continue;
            }

            boolean allowed = includePolygons.isEmpty();
            if (!allowed) {
                allowed = includePolygons.stream()
                        .filter(Objects::nonNull)
                        .anyMatch(frame -> overlapRatio(detectionPolygon, frame) >= Constant.DETECT_RATIO);
            }
            if (!allowed) {
                continue;
            }

            boolean blocked = blockPolygons.stream()
                    .filter(Objects::nonNull)
                    .anyMatch(frame -> overlapRatio(detectionPolygon, frame) >= Constant.BLOCK_RATIO);
            if (blocked) {
                continue;
            }

            List<Point> polygon = toPointList(result.getBox());
            results.add(new OcrDetectionResult(polygon, result.getText(), result.getScore()));
        }
        return results;
    }

    private List<Point> toPointList(double[][] box) {
        List<Point> points = new ArrayList<>(box.length);
        for (double[] coordinate : box) {
            if (coordinate.length < 2) {
                continue;
            }
            points.add(new Point((float) coordinate[0], (float) coordinate[1]));
        }
        return points;
    }

    private List<Polygon> toPolygons(ArrayList<ArrayList<Point>> frames) {
        if (CollectionUtils.isEmpty(frames)) {
            return Collections.emptyList();
        }
        return frames.stream()
                .map(this::polygonFromPoints)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private Polygon polygonFromBox(double[][] box) {
        if (box == null || box.length < 3) {
            return null;
        }
        Coordinate[] coordinates = new Coordinate[box.length + 1];
        for (int i = 0; i < box.length; i++) {
            coordinates[i] = new Coordinate(box[i][0], box[i][1]);
        }
        coordinates[box.length] = coordinates[0];
        try {
            return GEOMETRY_FACTORY.createPolygon(coordinates);
        } catch (IllegalArgumentException ex) {
            log.debug("Invalid detection polygon ignored: {}", Arrays.deepToString(box), ex);
            return null;
        }
    }

    private Polygon polygonFromPoints(List<Point> points) {
        if (points == null || points.size() < 3) {
            return null;
        }
        Coordinate[] coordinates = new Coordinate[points.size() + 1];
        for (int i = 0; i < points.size(); i++) {
            Point point = points.get(i);
            if (point == null || point.getX() == null || point.getY() == null) {
                return null;
            }
            coordinates[i] = new Coordinate(point.getX(), point.getY());
        }
        coordinates[points.size()] = coordinates[0];
        try {
            return GEOMETRY_FACTORY.createPolygon(coordinates);
        } catch (IllegalArgumentException ex) {
            log.debug("Invalid frame polygon ignored: {}", points, ex);
            return null;
        }
    }

    private double overlapRatio(Polygon detection, Polygon frame) {
        if (detection == null || frame == null) {
            return 0.0;
        }
        try {
            Geometry intersection = detection.intersection(frame);
            double detectionArea = detection.getArea();
            if (detectionArea <= 0) {
                return 0.0;
            }
            return intersection.getArea() / detectionArea;
        } catch (RuntimeException ex) {
            log.warn("Failed to compute polygon overlap", ex);
            return 0.0;
        }
    }

    private void drawDetections(BufferedImage image, List<OcrDetectionResult> detections) {
        if (detections == null || detections.isEmpty()) {
            return;
        }

        Graphics2D g2d = image.createGraphics();
        try {
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2d.setColor(Color.RED);
            g2d.setStroke(new BasicStroke(1.0f));

            Font baseFont = resolveLabelFont(Math.max(8, image.getWidth() / 80));
            g2d.setFont(baseFont);
            FontMetrics metrics = g2d.getFontMetrics();

            for (OcrDetectionResult detection : detections) {
                List<Point> polygon = detection.getPolygon();
                if (polygon == null || polygon.size() < 3) {
                    continue;
                }

                boolean invalid = false;
                Path2D.Double path = new Path2D.Double();
                double minX = Double.MAX_VALUE;
                double minY = Double.MAX_VALUE;
                double firstX = 0;
                double firstY = 0;
                for (int i = 0; i < polygon.size(); i++) {
                    Point vertex = polygon.get(i);
                    if (vertex == null || vertex.getX() == null || vertex.getY() == null) {
                        invalid = true;
                        break;
                    }
                    double x = vertex.getX();
                    double y = vertex.getY();
                    if (Double.isNaN(x) || Double.isNaN(y)) {
                        invalid = true;
                        break;
                    }
                    if (i == 0) {
                        path.moveTo(x, y);
                        firstX = x;
                        firstY = y;
                    } else {
                        path.lineTo(x, y);
                    }
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                }
                if (invalid) {
                    continue;
                }
                path.lineTo(firstX, firstY);
                path.closePath();
                g2d.draw(path);

                double labelX = minX;
                double labelY = Math.max(metrics.getAscent(), minY - 4);
                String label = detection.getText() == null
                        ? ""
                        : detection.getText().trim();
                if (!label.isEmpty()) {
                    label = label + String.format(Locale.US, " (%.2f)", detection.getConfidence());
                    g2d.drawString(label, (float) labelX, (float) labelY);
                }
            }
        } finally {
            g2d.dispose();
        }
    }

    private Font resolveLabelFont(int size) {
        String[] candidates = {
                "Microsoft YaHei",
                "SimHei",
                "SimSun",
                "PingFang SC",
                "Noto Sans CJK SC",
                "Arial Unicode MS",
                "Arial"
        };
        for (String name : candidates) {
            Font font = new Font(name, Font.BOLD, size);
            if (font.canDisplay('\u6D4B')) {
                return font;
            }
        }
        return new Font(Font.SANS_SERIF, Font.BOLD, size);
    }

    private PaddleOcrEngine selectEngine(String detectionLevel) {
        String normalized = normaliseDetectionLevel(detectionLevel);
        if ("heavy".equals(normalized)) {
            return heavyEngine;
        }
        return lightEngine;
    }

    private String normaliseDetectionLevel(String detectionLevel) {
        if (detectionLevel == null) {
            return "light";
        }
        String trimmed = detectionLevel.trim();
        if (trimmed.isEmpty()) {
            return "light";
        }
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (lower.contains("heavy")
                || lower.contains("det2")
                || lower.contains("large")
                || trimmed.contains("重")) {
            return "heavy";
        }
        return "light";
    }

    @PreDestroy
    public void shutdown() {
        tryClose(heavyEngine);
        tryClose(lightEngine);
    }

    private void tryClose(AutoCloseable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (Exception e) {
            log.warn("Failed to close OCR resource", e);
        }
    }

    private static boolean isTestEnvironment() {
        try {
            Class.forName("org.junit.jupiter.api.Test");
            return true;
        } catch (ClassNotFoundException ex) {
            return false;
        }
    }

    private record InferenceResult(BufferedImage image, List<OcrDetectionResult> detections) {
    }
}
