package com.yuqiangdede.ocr.service;

import ai.onnxruntime.OrtException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.yuqiangdede.common.dto.Point;
import com.yuqiangdede.common.util.ImageUtil;
import com.yuqiangdede.common.util.JsonUtils;
import com.yuqiangdede.ocr.config.Constant;
import com.yuqiangdede.ocr.dto.input.OcrDetectionRequest;
import com.yuqiangdede.ocr.dto.output.OcrDetectionResult;
import com.yuqiangdede.ocr.runtime.PaddleOcrEngine;
import com.yuqiangdede.ocr.util.OcrPrompt;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.opencv.core.Mat;
import org.springframework.stereotype.Service;
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
import java.util.List;
import java.util.Locale;

@Service
@Slf4j
public class OcrService {

    private static final String LEVEL_LITE = "lite";
    private static final String LEVEL_EX = "ex";

    private final PaddleOcrEngine lightEngine;
    private final PaddleOcrEngine heavyEngine;
    private final OcrPrompt ocrPrompt;

    static {
        // Load OpenCV's native binaries unless tests or configuration skip it.
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

    public OcrService(OcrPrompt ocrPrompt) {
        this.ocrPrompt = ocrPrompt;
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

    public String detectWithLLM(OcrDetectionRequest request) throws IOException, OrtException {
        InferenceResult inferenceResult = runInference(request);
        return ocrPrompt.fineTuning(inferenceResult.detections());

    }
    public String detectWithSR(OcrDetectionRequest request) throws IOException, OrtException {
        InferenceResult inferenceResult = runInference(request);
        return ocrPrompt.semanticReconstruction(inferenceResult.detections());
    }

    public BufferedImage detectWithOverlay(OcrDetectionRequest request) throws IOException, OrtException {
        InferenceResult inferenceResult = runInference(request);
        BufferedImage image = inferenceResult.image();
        drawDetections(image, inferenceResult.detections());
        return image;
    }

    public byte[] detectI(OcrDetectionRequest request) throws IOException, OrtException {
        BufferedImage image = detectWithOverlay(request);
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            javax.imageio.ImageIO.write(image, "jpg", baos);
            return baos.toByteArray();
        }
    }

    /**
     * Runs the full OCR pipeline for a single request: download image, infer text, convert results.
     */
    private InferenceResult runInference(OcrDetectionRequest request) throws IOException, OrtException {
        if (request == null) {
            throw new IllegalArgumentException("request must not be null");
        }
        if (ObjectUtils.isEmpty(request.getImgUrl())) {
            throw new IllegalArgumentException("imgUrl is null or empty");
        }

        long start = System.currentTimeMillis();
        // Materialise the source as both OpenCV matrix and buffered image for later reuse.
        Mat mat = ImageUtil.urlToMat(request.getImgUrl());
        BufferedImage image = ImageUtil.matToBufferedImage(mat);

        try {
            // Select the appropriate detector variant and execute ONNX inference.
            PaddleOcrEngine engine = selectEngine(request.getDetectionLevel());
            List<PaddleOcrEngine.OcrResult> rawResults = engine.ocr(mat);
            // Translate engine output into DTOs, removing entries that cannot form polygons.
            List<OcrDetectionResult> filtered = convertDetections(rawResults);
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

    /**
     * Converts raw OCR engine results into public DTOs while filtering invalid detections.
     */
    private List<OcrDetectionResult> convertDetections(List<PaddleOcrEngine.OcrResult> raw) {
        List<OcrDetectionResult> results = new ArrayList<>(raw.size());
        for (PaddleOcrEngine.OcrResult result : raw) {
            List<Point> polygon = toPointList(result.getBox());
            if (polygon.size() < 3) {
                continue;
            }
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

    /**
     * Renders detection polygons and optional labels on top of the original image.
     */
    private void drawDetections(BufferedImage image, List<OcrDetectionResult> detections) {
        if (detections == null || detections.isEmpty()) {
            return;
        }

        Graphics2D g2d = image.createGraphics();
        try {
            // Enable anti-aliasing so both shapes and text annotations remain crisp.
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
                    g2d.drawString(label, (float) labelX, (float) labelY);
                }
            }
        } finally {
            g2d.dispose();
        }
    }

    /**
     * Finds a font capable of rendering Chinese characters to keep label text readable.
     */
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
            if (font.canDisplay('测')) {
                return font;
            }
        }
        return new Font(Font.SANS_SERIF, Font.BOLD, size);
    }

    /**
     * Chooses between the lightweight and heavyweight OCR engines.
     */
    private PaddleOcrEngine selectEngine(String detectionLevel) {
        String normalized = normaliseDetectionLevel(detectionLevel);
        if (LEVEL_EX.equals(normalized)) {
            return heavyEngine;
        }
        return lightEngine;
    }

    /**
     * Maps various user inputs into a bounded detection level vocabulary.
     */
    private String normaliseDetectionLevel(String detectionLevel) {
        if (detectionLevel == null) {
            return LEVEL_LITE;
        }
        String trimmed = detectionLevel.trim();
        if (trimmed.isEmpty()) {
            return LEVEL_LITE;
        }
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (isExLevel(lower, trimmed)) {
            return LEVEL_EX;
        }
        if (isLiteLevel(lower)) {
            return LEVEL_LITE;
        }
        return LEVEL_LITE;
    }

    private boolean isExLevel(String lower, String original) {
        return lower.equals(LEVEL_EX)
                || lower.equals("extra")
                || lower.equals("ex-large")
                || lower.equals("exlarge")
                || lower.contains("heavy")
                || lower.contains("det2")
                || lower.contains("large")
                || original.contains("重");
    }

    private boolean isLiteLevel(String lower) {
        return lower.equals("light")
                || lower.equals("lite")
                || lower.equals("default")
                || lower.contains("det")
                || lower.contains("base");
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

    /**
     * Runs OCR inference, applies LLM refinement and returns an annotated JPEG payload.
     */
    public byte[] detectWithLLMI(OcrDetectionRequest request) throws IOException, OrtException {
        InferenceResult inferenceResult = runInference(request);
        List<OcrDetectionResult> originalDetections = inferenceResult.detections();
        List<OcrDetectionResult> fineTunedDetections = applyFineTuning(originalDetections);

        BufferedImage image = inferenceResult.image();
        drawDetections(image, fineTunedDetections);

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            javax.imageio.ImageIO.write(image, "jpg", baos);
            return baos.toByteArray();
        }
    }

    /**
     * Reconciles LLM-generated corrections with original detections, preserving geometry when needed.
     */
    private List<OcrDetectionResult> applyFineTuning(List<OcrDetectionResult> originalDetections) {
        if (originalDetections == null || originalDetections.isEmpty()) {
            return originalDetections;
        }
        try {
            String fineTunedJson = ocrPrompt.fineTuning(originalDetections);
            if (fineTunedJson == null || fineTunedJson.isBlank()) {
                return originalDetections;
            }
            List<OcrDetectionResult> parsed = JsonUtils.objectMapper.readValue(
                    fineTunedJson,
                    new TypeReference<List<OcrDetectionResult>>() {
                    }
            );
            if (parsed == null || parsed.isEmpty()) {
                return originalDetections;
            }
            if (parsed.size() != originalDetections.size()) {
                log.warn("Fine-tuned detection size mismatch, original={}, tuned={}",
                        originalDetections.size(), parsed.size());
                return originalDetections;
            }
            List<OcrDetectionResult> merged = new ArrayList<>(originalDetections.size());
            for (int i = 0; i < originalDetections.size(); i++) {
                OcrDetectionResult source = originalDetections.get(i);
                OcrDetectionResult tuned = parsed.get(i);

                List<Point> polygon = (tuned.getPolygon() == null || tuned.getPolygon().isEmpty())
                        ? source.getPolygon()
                        : tuned.getPolygon();
                String text = tuned.getText() != null ? tuned.getText() : source.getText();
                double confidence = tuned.getConfidence() > 0 ? tuned.getConfidence() : source.getConfidence();

                merged.add(new OcrDetectionResult(polygon, text, confidence));
            }
            return merged;
        } catch (Exception ex) {
            log.warn("Failed to apply LLM fine-tuning, fallback to original detections", ex);
            return originalDetections;
        }
    }


    private record InferenceResult(BufferedImage image, List<OcrDetectionResult> detections) {
    }
}
