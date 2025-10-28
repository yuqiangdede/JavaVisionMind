package com.yuqiangdede.common;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import ai.onnxruntime.OrtSession.Result;
import ai.onnxruntime.OrtSession.SessionOptions;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import javax.imageio.ImageIO;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.PrecisionModel;
import org.locationtech.jts.operation.buffer.BufferOp;
import org.locationtech.jts.operation.buffer.BufferParameters;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.MatOfByte;
import org.opencv.core.Point;
import org.opencv.core.RotatedRect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

/**
 * Standalone Java counterpart of {@code test_ocr.py}.
 *
 * <p>This class demonstrates how to load the ONNX-based PaddleOCR detector/recognizer/classifier
 * models, run them against an input image, and print the OCR results.</p>
 *
 * <p>Usage:</p>
 * <pre>
 *   java -cp your-classpath TestOcr [imagePath] [modelsRoot]
 * </pre>
 */
public final class TestOcr {

    private TestOcr() {
    }

    public static void main(String[] args) throws Exception {
        try {
            System.load("C:\\Users\\Administrator\\Documents\\Code\\JavaCode\\JavaVisionMind\\resource\\lib\\opencv\\opencv_java490.dll");
        } catch (UnsatisfiedLinkError e) {
            throw new IllegalStateException("Failed to load OpenCV native library", e);
        }

        String imagePath = "https://33440429.s21i.faiusr.com/2/ABUIABACGAAgiY-OwgYogOzW9QMwgA84_Ag.jpg";
        Path detModel = Path.of("E:\\TestSth\\OnnxOCR\\onnxocr\\models\\ppocrv5\\det\\det.onnx");
        Path recModel = Path.of("E:\\TestSth\\OnnxOCR\\onnxocr\\models\\ppocrv5\\rec\\rec.onnx");
        Path clsModel = Path.of("E:\\TestSth\\OnnxOCR\\onnxocr\\models\\ppocrv5\\cls\\cls.onnx");
        Path dictPath = Path.of("E:\\TestSth\\OnnxOCR\\onnxocr\\models\\ppocrv5\\ppocrv5_dict.txt");

        Mat image = loadImage(imagePath);

        try (ONNXPaddleOcr ocr = new ONNXPaddleOcr(detModel, recModel, clsModel, dictPath, true)) {
            Instant start = Instant.now();
            List<OcrResult> results = ocr.ocr(image);
            Instant end = Instant.now();
            double elapsedSeconds = Duration.between(start, end).toNanos() / 1_000_000_000.0;
            System.out.printf(Locale.US, "total time: %.3f%n", elapsedSeconds);
            System.out.println("result:");
            for (OcrResult result : results) {
                System.out.println(result);
            }
            Path outputImage = resolveOutputImagePath(imagePath);
            saveVisualization(image, results, outputImage);
            System.out.println("visualization saved to: " + outputImage);
        }
        image.release();
    }

    private static Mat loadImage(String imagePath) throws IOException {
        if (imagePath == null || imagePath.isBlank()) {
            throw new IllegalArgumentException("imagePath must not be null or empty");
        }

        if (isHttpUrl(imagePath)) {
            byte[] data = downloadBytes(imagePath);
            MatOfByte buffer = new MatOfByte(data);
            Mat image = Imgcodecs.imdecode(buffer, Imgcodecs.IMREAD_COLOR);
            buffer.release();
            if (image == null || image.empty()) {
                throw new IllegalArgumentException("Unable to decode image from URL: " + imagePath);
            }
            return image;
        }

        Mat image = Imgcodecs.imread(imagePath);
        if (image == null || image.empty()) {
            throw new IllegalArgumentException("Unable to read image: " + imagePath);
        }
        return image;
    }

    private static boolean isHttpUrl(String path) {
        try {
            URI uri = new URI(path);
            String scheme = uri.getScheme();
            return "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
        } catch (URISyntaxException e) {
            return false;
        }
    }

    private static byte[] downloadBytes(String url) throws IOException {
        URLConnection connection = new URL(url).openConnection();
        connection.setConnectTimeout(10_000);
        connection.setReadTimeout(10_000);
        try (InputStream in = connection.getInputStream();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            return out.toByteArray();
        }
    }

    private static Path resolveOutputImagePath(String imagePath) {
        if (isHttpUrl(imagePath)) {
            String fileName = extractFileName(imagePath);
            String baseName = stripExtension(fileName);
            return Path.of(baseName + "_java_ocr_result.jpg");
        }
        return Path.of(imagePath).resolveSibling("java_ocr_result.jpg");
    }

    private static String extractFileName(String imagePath) {
        try {
            URI uri = new URI(imagePath);
            String path = uri.getPath();
            if (path == null || path.isEmpty()) {
                return "remote_image";
            }
            Path fileName = Path.of(path).getFileName();
            if (fileName == null) {
                return "remote_image";
            }
            String name = fileName.toString();
            return name.isBlank() ? "remote_image" : name;
        } catch (URISyntaxException e) {
            return "remote_image";
        }
    }

    private static String stripExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot <= 0) {
            return filename;
        }
        return filename.substring(0, dot);
    }

    private static void saveVisualization(Mat source, List<OcrResult> results, Path outputPath) {
        Mat vis = source.clone();
        Scalar boxColor = new Scalar(0, 255, 0);
        int boxThickness = 2;
        List<TextOverlay> overlays = new ArrayList<>();

        for (OcrResult result : results) {
            Point[] pts = new Point[4];
            for (int i = 0; i < 4; i++) {
                double x = Math.max(0, Math.min(source.width() - 1, result.box[i][0]));
                double y = Math.max(0, Math.min(source.height() - 1, result.box[i][1]));
                pts[i] = new Point(x, y);
            }

            MatOfPoint poly = new MatOfPoint();
            poly.fromArray(pts);
            Imgproc.polylines(vis, Collections.singletonList(poly), true, boxColor, boxThickness);
            poly.release();

            double minY = Arrays.stream(pts).mapToDouble(p -> p.y).min().orElse(pts[0].y);
            double minX = Arrays.stream(pts).mapToDouble(p -> p.x).min().orElse(pts[0].x);
            double textY = Math.max(0, minY - 6);
            overlays.add(new TextOverlay(result.text, new Point(minX, textY)));
        }

        BufferedImage buffered = matToBufferedImage(vis);
        Graphics2D g2d = buffered.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        Font font = new Font("Microsoft YaHei", Font.PLAIN, 18);
        g2d.setFont(font);
        g2d.setColor(new Color(255, 0, 0));
        for (TextOverlay overlay : overlays) {
            int x = (int) Math.round(overlay.position.x);
            int y = (int) Math.round(overlay.position.y);
            int baseline = font.getSize();
            if (y < baseline) {
                y = baseline;
            }
            g2d.drawString(overlay.text, x, y);
        }
        g2d.dispose();

        try {
            if (outputPath.getParent() != null) {
                Files.createDirectories(outputPath.getParent());
            }
            ImageIO.write(buffered, "jpg", outputPath.toFile());
        } catch (IOException e) {
            throw new RuntimeException("Failed to write visualization image", e);
        }

        vis.release();
    }

    private static BufferedImage matToBufferedImage(Mat mat) {
        int type;
        if (mat.channels() == 1) {
            type = BufferedImage.TYPE_BYTE_GRAY;
        } else if (mat.channels() == 3) {
            type = BufferedImage.TYPE_3BYTE_BGR;
        } else if (mat.channels() == 4) {
            type = BufferedImage.TYPE_4BYTE_ABGR;
        } else {
            throw new IllegalArgumentException("Unsupported Mat channel count: " + mat.channels());
        }

        BufferedImage image = new BufferedImage(mat.cols(), mat.rows(), type);
        byte[] data = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();
        mat.get(0, 0, data);
        return image;
    }

    private static final class ONNXPaddleOcr implements AutoCloseable {
        private static final GeometryFactory GEOMETRY_FACTORY =
                new GeometryFactory(new PrecisionModel(), 0);
        private static final BufferParameters BUFFER_PARAMETERS = createBufferParameters();

        private final OrtEnvironment env;
        private final SessionOptions sessionOptions;
        private final OrtSession detSession;
        private final OrtSession recSession;
        private final OrtSession clsSession;
        private final String detInputName;
        private final String recInputName;
        private final String clsInputName;
        private final double detDbThresh = 0.3;
        private final double detDbBoxThresh = 0.6;
        private final double detDbUnclipRatio = 1.5;
        private final int detMaxCandidates = 1000;
        private final double detMinSize = 3.0;
        private final boolean useAngleCls;
        private final int detLimitSideLen = 960;
        private final String[] labelList = {"0", "180"};
        private final int clsBatchNum = 6;
        private final double clsThresh = 0.9;
        private final int clsImgC = 3;
        private final int clsImgH = 48;
        private final int clsImgW = 192;
        private final int recBatchNum = 6;
        private final int recImgC = 3;
        private final int recImgH = 48;
        private final int recImgW = 320;
        private final double dropScore = 0.5;
        private final List<String> dictionary;

        ONNXPaddleOcr(Path detModel,
                      Path recModel,
                      Path clsModel,
                      Path dictPath,
                      boolean useAngleCls) throws OrtException, IOException {
            this.useAngleCls = useAngleCls && Files.exists(clsModel);

            env = OrtEnvironment.getEnvironment();
            sessionOptions = new SessionOptions();
            sessionOptions.setOptimizationLevel(SessionOptions.OptLevel.ALL_OPT);

            detSession = env.createSession(detModel.toString(), sessionOptions);
            detInputName = detSession.getInputNames().iterator().next();

            recSession = env.createSession(recModel.toString(), sessionOptions);
            recInputName = recSession.getInputNames().iterator().next();

            if (this.useAngleCls) {
                clsSession = env.createSession(clsModel.toString(), sessionOptions);
                clsInputName = clsSession.getInputNames().iterator().next();
            } else {
                clsSession = null;
                clsInputName = null;
            }

            dictionary = loadDictionary(dictPath);
        }

        private static BufferParameters createBufferParameters() {
            BufferParameters params = new BufferParameters();
            params.setJoinStyle(BufferParameters.JOIN_ROUND);
            params.setEndCapStyle(BufferParameters.CAP_ROUND);
            return params;
        }

        public List<OcrResult> ocr(Mat srcBgr) throws OrtException {
            Mat image = srcBgr.clone();
            try {
                Size originalSize = image.size();
                ResizeInfo resizeInfo = resizeForDetection(image, detLimitSideLen);

                float[] detInput = normalizeToCHW(
                        image,
                        new float[]{0.485f, 0.456f, 0.406f},
                        new float[]{0.229f, 0.224f, 0.225f},
                        1.0f / 255.0f);

                long[] detShape = {1L, 3L, image.height(), image.width()};
                List<Detection> detections;
                try (OnnxTensor tensor =
                             OnnxTensor.createTensor(env, FloatBuffer.wrap(detInput), detShape);
                     Result detResult =
                             detSession.run(Collections.singletonMap(detInputName, tensor))) {
                    float[][][][] detMaps = (float[][][][]) detResult.get(0).getValue();
                    detections = postProcessDetection(detMaps[0][0], resizeInfo, originalSize);
                }

                if (detections.isEmpty()) {
                    return Collections.emptyList();
                }

                List<Mat> crops = new ArrayList<>(detections.size());
                for (Detection detection : detections) {
                    crops.add(extractCrop(srcBgr, detection.box));
                }

                if (useAngleCls) {
                    applyAngleClassification(crops);
                }

                List<TextPrediction> recResults = runRecognition(crops);
                List<OcrResult> results = new ArrayList<>();
                int count = Math.min(detections.size(), recResults.size());
                for (int i = 0; i < count; i++) {
                    TextPrediction prediction = recResults.get(i);
                    if (prediction.score >= dropScore) {
                        results.add(
                                new OcrResult(detections.get(i).box, prediction.text, prediction.score));
                    }
                }
                return results;
            } finally {
                image.release();
            }
        }

        @Override
        public void close() throws Exception {
            if (clsSession != null) {
                clsSession.close();
            }
            recSession.close();
            detSession.close();
            sessionOptions.close();
            env.close();
        }

        private void applyAngleClassification(List<Mat> crops) throws OrtException {
            if (crops.isEmpty()) {
                return;
            }

            for (int start = 0; start < crops.size(); start += clsBatchNum) {
                int end = Math.min(crops.size(), start + clsBatchNum);
                int batch = end - start;
                float[] batchData = new float[batch * clsImgC * clsImgH * clsImgW];

                for (int i = 0; i < batch; i++) {
                    float[] normalized = normalizeForCls(crops.get(start + i));
                    System.arraycopy(normalized, 0, batchData, i * normalized.length, normalized.length);
                }

                long[] shape = {batch, clsImgC, clsImgH, clsImgW};
                try (OnnxTensor input =
                             OnnxTensor.createTensor(env, FloatBuffer.wrap(batchData), shape);
                     Result output =
                             clsSession.run(Collections.singletonMap(clsInputName, input))) {
                    float[][] probs = (float[][]) output.get(0).getValue();
                    for (int i = 0; i < probs.length; i++) {
                        float[] row = probs[i];
                        int maxIdx = 0;
                        float maxVal = row[0];
                        for (int j = 1; j < row.length; j++) {
                            if (row[j] > maxVal) {
                                maxVal = row[j];
                                maxIdx = j;
                            }
                        }
                        if ("180".equals(labelList[maxIdx]) && maxVal > clsThresh) {
                            Mat rotated = new Mat();
                            Core.rotate(crops.get(start + i), rotated, Core.ROTATE_180);
                            crops.get(start + i).release();
                            crops.set(start + i, rotated);
                        }
                    }
                }
            }
        }

        private List<TextPrediction> runRecognition(List<Mat> crops) throws OrtException {
            if (crops.isEmpty()) {
                return Collections.emptyList();
            }

            int[] order = sortByAspectRatio(crops);
            TextPrediction[] orderedResults = new TextPrediction[crops.size()];

            for (int start = 0; start < crops.size(); start += recBatchNum) {
                int end = Math.min(crops.size(), start + recBatchNum);
                int batch = end - start;

                List<float[]> batchImages = new ArrayList<>(batch);
                double maxWhRatio = ((double) recImgW) / recImgH;

                for (int idx = start; idx < end; idx++) {
                    Mat mat = crops.get(order[idx]);
                    double ratio = (double) mat.cols() / Math.max(mat.rows(), 1);
                    if (ratio > maxWhRatio) {
                        maxWhRatio = ratio;
                    }
                }

                for (int idx = start; idx < end; idx++) {
                    Mat mat = crops.get(order[idx]);
                    batchImages.add(resizeAndNormalizeForRec(mat, maxWhRatio));
                }

                float[] batchData = concat(batchImages);
                long[] shape = {batch, recImgC, recImgH, recImgW};

                try (OnnxTensor input =
                             OnnxTensor.createTensor(env, FloatBuffer.wrap(batchData), shape);
                     Result output =
                             recSession.run(Collections.singletonMap(recInputName, input))) {
                    float[][][] logits = (float[][][]) output.get(0).getValue();
                    for (int i = 0; i < logits.length; i++) {
                        orderedResults[order[start + i]] = decodeCtc(logits[i]);
                    }
                }
            }

            return Arrays.asList(orderedResults);
        }

        private TextPrediction decodeCtc(float[][] logits) {
            int blankIndex = 0;
            StringBuilder sb = new StringBuilder();
            List<Float> probs = new ArrayList<>();
            int prevIndex = -1;
            for (float[] logit : logits) {
                int maxIdx = 0;
                float maxVal = logit[0];
                for (int c = 1; c < logit.length; c++) {
                    if (logit[c] > maxVal) {
                        maxVal = logit[c];
                        maxIdx = c;
                    }
                }
                if (maxIdx == blankIndex) {
                    prevIndex = maxIdx;
                    continue;
                }
                if (maxIdx >= dictionary.size()) {
                    prevIndex = maxIdx;
                    continue;
                }
                if (maxIdx == prevIndex) {
                    continue;
                }
                prevIndex = maxIdx;
                String ch = dictionary.get(maxIdx);
                sb.append(ch);
                probs.add(maxVal);
            }
            double score = probs.isEmpty()
                    ? 0.0
                    : probs.stream().mapToDouble(v -> v).average().orElse(0.0);
            return new TextPrediction(sb.toString(), score);
        }

        private float[] concat(List<float[]> items) {
            int total = items.stream().mapToInt(arr -> arr.length).sum();
            float[] result = new float[total];
            int offset = 0;
            for (float[] arr : items) {
                System.arraycopy(arr, 0, result, offset, arr.length);
                offset += arr.length;
            }
            return result;
        }

        private int[] sortByAspectRatio(List<Mat> mats) {
            Integer[] indices = new Integer[mats.size()];
            for (int i = 0; i < indices.length; i++) {
                indices[i] = i;
            }
            Arrays.sort(indices, Comparator.comparingDouble(
                    idx -> mats.get(idx).cols() / (double) Math.max(mats.get(idx).rows(), 1)));
            int[] result = new int[indices.length];
            for (int i = 0; i < indices.length; i++) {
                result[i] = indices[i];
            }
            return result;
        }

        private float[] resizeAndNormalizeForRec(Mat crop, double maxWhRatio) {
            Mat resized = new Mat();
            int targetW = (int) Math.ceil(recImgH * maxWhRatio);
            targetW = Math.min(targetW, recImgW);

            double ratio = (double) crop.cols() / Math.max(crop.rows(), 1);
            int resizedW = ratio > ((double) targetW) / recImgH
                    ? targetW
                    : (int) Math.ceil(recImgH * ratio);

            Size size = new Size(Math.max(resizedW, 1), recImgH);
            Imgproc.resize(crop, resized, size, 0, 0, Imgproc.INTER_CUBIC);

            byte[] data = new byte[resized.rows() * resized.cols() * resized.channels()];
            resized.get(0, 0, data);

            float[] result = new float[recImgC * recImgH * recImgW];
            Arrays.fill(result, 0f);
            int hw = recImgH * recImgW;

            for (int y = 0; y < resized.rows(); y++) {
                for (int x = 0; x < resized.cols(); x++) {
                    int idx = (y * resized.cols() + x) * resized.channels();
                    int dst = y * recImgW + x;
                    for (int c = 0; c < resized.channels(); c++) {
                        float value = (data[idx + c] & 0xFF) / 255.0f;
                        value = (value - 0.5f) / 0.5f;
                        result[c * hw + dst] = value;
                    }
                }
            }

            resized.release();
            return result;
        }

        private float[] normalizeForCls(Mat crop) {
            Mat resized = new Mat();
            double ratio = (double) crop.cols() / Math.max(crop.rows(), 1);
            int resizedW = ratio > ((double) clsImgW) / clsImgH
                    ? clsImgW
                    : (int) Math.ceil(clsImgH * ratio);
            Size targetSize = new Size(Math.max(resizedW, 1), clsImgH);
            Imgproc.resize(crop, resized, targetSize, 0, 0, Imgproc.INTER_CUBIC);

            byte[] data = new byte[resized.rows() * resized.cols() * resized.channels()];
            resized.get(0, 0, data);

            float[] result = new float[clsImgC * clsImgH * clsImgW];
            Arrays.fill(result, 0f);
            int hw = clsImgH * clsImgW;
            for (int y = 0; y < resized.rows(); y++) {
                for (int x = 0; x < resized.cols(); x++) {
                    int srcIdx = (y * resized.cols() + x) * resized.channels();
                    int dstIdx = y * clsImgW + x;
                    for (int c = 0; c < resized.channels(); c++) {
                        float value = (data[srcIdx + c] & 0xFF) / 255.0f;
                        value = (value - 0.5f) / 0.5f;
                        result[c * hw + dstIdx] = value;
                    }
                }
            }

            resized.release();
            return result;
        }

        private float[] normalizeToCHW(Mat mat, float[] mean, float[] std, float scale) {
            byte[] data = new byte[mat.rows() * mat.cols() * mat.channels()];
            mat.get(0, 0, data);
            float[] result = new float[mat.channels() * mat.rows() * mat.cols()];
            int hw = mat.rows() * mat.cols();
            for (int y = 0; y < mat.rows(); y++) {
                for (int x = 0; x < mat.cols(); x++) {
                    int idx = (y * mat.cols() + x) * mat.channels();
                    int dest = y * mat.cols() + x;
                    for (int c = 0; c < mat.channels(); c++) {
                        float value = (data[idx + c] & 0xFF) * scale;
                        value = (value - mean[c]) / std[c];
                        result[c * hw + dest] = value;
                    }
                }
            }
            return result;
        }

        private ResizeInfo resizeForDetection(Mat mat, int limitSideLen) {
            int h = mat.rows();
            int w = mat.cols();
            double ratio;
            if (Math.max(h, w) > limitSideLen) {
                ratio = limitSideLen / (double) Math.max(h, w);
            } else {
                ratio = 1.0;
            }

            int newH = Math.max((int) Math.round((h * ratio) / 32.0) * 32, 32);
            int newW = Math.max((int) Math.round((w * ratio) / 32.0) * 32, 32);
            Imgproc.resize(mat, mat, new Size(newW, newH));
            double ratioH = newH / (double) h;
            double ratioW = newW / (double) w;
            return new ResizeInfo(ratioH, ratioW);
        }

        private List<Detection> postProcessDetection(float[][] pred,
                                                     ResizeInfo resizeInfo,
                                                     Size originalSize) {
            int h = pred.length;
            int w = pred[0].length;
            Mat bitmap = new Mat(h, w, CvType.CV_8UC1);
            Mat predMat = new Mat(h, w, CvType.CV_32FC1);

            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    float val = pred[y][x];
                    predMat.put(y, x, val);
                    bitmap.put(y, x, val > detDbThresh ? 255 : 0);
                }
            }

            List<MatOfPoint> contours = new ArrayList<>();
            Imgproc.findContours(bitmap, contours, new Mat(), Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE);

            List<Detection> detections = new ArrayList<>();
            int limit = Math.min(contours.size(), detMaxCandidates);
            for (int idx = 0; idx < limit; idx++) {
                MatOfPoint contour = contours.get(idx);
                if (contour.rows() < 4) {
                    contour.release();
                    continue;
                }

                Mat mask = Mat.zeros(predMat.size(), CvType.CV_8UC1);
                Imgproc.drawContours(mask, Collections.singletonList(contour), 0, new Scalar(255), -1);
                Scalar mean = Core.mean(predMat, mask);
                mask.release();

                double score = mean.val[0];
                if (score < detDbBoxThresh) {
                    contour.release();
                    continue;
                }

                Point[] contourPoints = contour.toArray();
                MatOfPoint2f contour2f = new MatOfPoint2f(contourPoints);
                RotatedRect rect = Imgproc.minAreaRect(contour2f);
                contour2f.release();

                if (minSideLength(rect) < detMinSize) {
                    contour.release();
                    continue;
                }

                Point[] expandedPoly = unclipPolygon(contourPoints, detDbUnclipRatio);
                if (expandedPoly == null || expandedPoly.length == 0) {
                    contour.release();
                    continue;
                }

                MatOfPoint2f expandedContour = new MatOfPoint2f(expandedPoly);
                RotatedRect expandedRect = Imgproc.minAreaRect(expandedContour);
                expandedContour.release();

                if (minSideLength(expandedRect) < detMinSize + 2.0) {
                    contour.release();
                    continue;
                }

                Point[] points = new Point[4];
                expandedRect.points(points);
                double[][] box = orderPoints(points);
                scaleBox(box, resizeInfo, originalSize);

                detections.add(new Detection(box, score));
                contour.release();
            }

            bitmap.release();
            predMat.release();

            detections.sort((a, b) -> {
                double diffY = a.box[0][1] - b.box[0][1];
                if (Math.abs(diffY) > 10) {
                    return Double.compare(a.box[0][1], b.box[0][1]);
                }
                return Double.compare(a.box[0][0], b.box[0][0]);
            });
            return detections;
        }

        private void scaleBox(double[][] box, ResizeInfo info, Size original) {
            double width = original.width;
            double height = original.height;
            for (double[] point : box) {
                point[0] = Math.min(Math.max(point[0] / info.ratioW, 0), width - 1);
                point[1] = Math.min(Math.max(point[1] / info.ratioH, 0), height - 1);
            }
        }

        private double[][] orderPoints(Point[] pts) {
            double[][] box = new double[4][2];
            Arrays.sort(pts, Comparator.comparingDouble(p -> p.x));

            Point leftTop;
            Point leftBottom;
            Point rightTop;
            Point rightBottom;

            if (pts[0].y < pts[1].y) {
                leftTop = pts[0];
                leftBottom = pts[1];
            } else {
                leftTop = pts[1];
                leftBottom = pts[0];
            }

            if (pts[2].y < pts[3].y) {
                rightTop = pts[2];
                rightBottom = pts[3];
            } else {
                rightTop = pts[3];
                rightBottom = pts[2];
            }

            box[0][0] = leftTop.x;
            box[0][1] = leftTop.y;
            box[1][0] = rightTop.x;
            box[1][1] = rightTop.y;
            box[2][0] = rightBottom.x;
            box[2][1] = rightBottom.y;
            box[3][0] = leftBottom.x;
            box[3][1] = leftBottom.y;
            return box;
        }

        private Point[] unclipPolygon(Point[] contour, double unclipRatio) {
            if (contour == null || contour.length < 3) {
                return null;
            }

            Coordinate[] coordinates = new Coordinate[contour.length + 1];
            for (int i = 0; i < contour.length; i++) {
                coordinates[i] = new Coordinate(contour[i].x, contour[i].y);
            }
            coordinates[contour.length] = new Coordinate(contour[0].x, contour[0].y);

            LinearRing ring;
            try {
                ring = GEOMETRY_FACTORY.createLinearRing(coordinates);
            } catch (IllegalArgumentException ex) {
                return null;
            }
            Polygon polygon = GEOMETRY_FACTORY.createPolygon(ring);
            double area = Math.abs(polygon.getArea());
            double perimeter = polygon.getLength();
            if (perimeter < 1e-6 || area < 1e-6) {
                return null;
            }

            double distance = area * unclipRatio / Math.max(perimeter, 1e-6);
            if (distance < 1e-6) {
                return contour.clone();
            }

            Geometry buffered;
            try {
                buffered = BufferOp.bufferOp(polygon, distance, BUFFER_PARAMETERS);
            } catch (RuntimeException ex) {
                return null;
            }

            if (buffered == null || buffered.isEmpty()) {
                return null;
            }

            Geometry largest = selectLargestPolygon(buffered);
            if (!(largest instanceof Polygon)) {
                return null;
            }

            Coordinate[] coords = ((Polygon) largest).getExteriorRing().getCoordinates();
            if (coords.length < 4) {
                return null;
            }

            Point[] result = new Point[coords.length - 1];
            for (int i = 0; i < coords.length - 1; i++) {
                result[i] = new Point(coords[i].x, coords[i].y);
            }
            return result;
        }

        private Geometry selectLargestPolygon(Geometry geometry) {
            if (geometry instanceof Polygon) {
                return geometry;
            }

            Geometry largest = null;
            double maxArea = -1.0;
            for (int i = 0; i < geometry.getNumGeometries(); i++) {
                Geometry component = geometry.getGeometryN(i);
                if (component instanceof Polygon) {
                    double area = component.getArea();
                    if (area > maxArea) {
                        maxArea = area;
                        largest = component;
                    }
                }
            }
            return largest;
        }

        private double minSideLength(RotatedRect rect) {
            return Math.min(rect.size.width, rect.size.height);
        }

        private Mat extractCrop(Mat image, double[][] box) {
            MatOfPoint2f srcPts = new MatOfPoint2f(
                    new Point(box[0][0], box[0][1]),
                    new Point(box[1][0], box[1][1]),
                    new Point(box[2][0], box[2][1]),
                    new Point(box[3][0], box[3][1]));

            double widthA = distance(box[0], box[1]);
            double widthB = distance(box[2], box[3]);
            double maxWidth = Math.max(widthA, widthB);

            double heightA = distance(box[0], box[3]);
            double heightB = distance(box[1], box[2]);
            double maxHeight = Math.max(heightA, heightB);

            Size targetSize = new Size(maxWidth, maxHeight);

            MatOfPoint2f dstPts = new MatOfPoint2f(
                    new Point(0, 0),
                    new Point(targetSize.width, 0),
                    new Point(targetSize.width, targetSize.height),
                    new Point(0, targetSize.height));

            Mat perspective = Imgproc.getPerspectiveTransform(srcPts, dstPts);
            Mat dst = new Mat();
            Imgproc.warpPerspective(
                    image, dst, perspective, targetSize, Imgproc.INTER_CUBIC, Core.BORDER_REPLICATE);

            if ((double) dst.rows() / Math.max(dst.cols(), 1) >= 1.5) {
                Mat rotated = new Mat();
                Core.rotate(dst, rotated, Core.ROTATE_90_COUNTERCLOCKWISE);
                dst.release();
                dst = rotated;
            }

            srcPts.release();
            dstPts.release();
            perspective.release();
            return dst;
        }

        private double distance(double[] a, double[] b) {
            double dx = a[0] - b[0];
            double dy = a[1] - b[1];
            return Math.hypot(dx, dy);
        }

        private List<String> loadDictionary(Path dictPath) throws IOException {
            List<String> lines = new ArrayList<>();
            try (BufferedReader reader =
                         Files.newBufferedReader(dictPath, StandardCharsets.UTF_8)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    lines.add(line.trim());
                }
            }
            List<String> dict = new ArrayList<>(lines.size() + 1);
            dict.add("");
            dict.addAll(lines);
            dict.add(" "); // trailing space token matches PaddleOCR's extra class
            return dict;
        }
    }

    private static final class ResizeInfo {
        final double ratioH;
        final double ratioW;

        ResizeInfo(double ratioH, double ratioW) {
            this.ratioH = ratioH;
            this.ratioW = ratioW;
        }
    }

    private static final class Detection {
        final double[][] box;
        final double score;

        Detection(double[][] box, double score) {
            this.box = Objects.requireNonNull(box);
            this.score = score;
        }
    }

    private static final class TextOverlay {
        final String text;
        final Point position;

        TextOverlay(String text, Point position) {
            this.text = Objects.requireNonNull(text);
            this.position = Objects.requireNonNull(position);
        }
    }

    private static final class TextPrediction {
        final String text;
        final double score;

        TextPrediction(String text, double score) {
            this.text = Objects.requireNonNull(text);
            this.score = score;
        }
    }

    private static final class OcrResult {
        final double[][] box;
        final String text;
        final double score;

        OcrResult(double[][] box, String text, double score) {
            this.box = Objects.requireNonNull(box);
            this.text = Objects.requireNonNull(text);
            this.score = score;
        }

        @Override
        public String toString() {
            String boxStr = Arrays.stream(box)
                    .map(pt -> String.format(Locale.US, "[%.1f, %.1f]", pt[0], pt[1]))
                    .collect(Collectors.joining(", ", "[", "]"));
            return String.format(Locale.US, "%s, %.3f, \"%s\"", boxStr, score, text);
        }
    }
}
