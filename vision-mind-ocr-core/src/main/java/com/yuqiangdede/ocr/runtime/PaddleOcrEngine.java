package com.yuqiangdede.ocr.runtime;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import ai.onnxruntime.OrtSession.Result;
import ai.onnxruntime.OrtSession.SessionOptions;
import lombok.Getter;
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
import org.opencv.core.MatOfByte;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.RotatedRect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * ONNX-powered Paddle OCR pipeline adapted from the standalone TestOcr utility.
 */
public class PaddleOcrEngine implements AutoCloseable {

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

    public PaddleOcrEngine(Path detModel,
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
            List<float[]> inputs = new ArrayList<>(batch);
            for (int idx = start; idx < end; idx++) {
                inputs.add(normalizeForCls(crops.get(idx)));
            }
            float[] batchData = concat(inputs);
            long[] shape = {batch, clsImgC, clsImgH, clsImgW};
            try (OnnxTensor tensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(batchData), shape);
                 Result result = clsSession.run(Collections.singletonMap(clsInputName, tensor))) {
                float[][] scores = (float[][]) result.get(0).getValue();
                for (int i = 0; i < scores.length; i++) {
                    float[] score = scores[i];
                    int argMax = argMax(score);
                    double confidence = score[argMax];
                    if (confidence > clsThresh && labelList[argMax].equals("180")) {
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
        List<TextPrediction> results = new ArrayList<>(crops.size());
        for (int start = 0; start < crops.size(); start += recBatchNum) {
            int end = Math.min(crops.size(), start + recBatchNum);
            List<Mat> batchCrops = crops.subList(start, end);
            int batch = batchCrops.size();
            int[] sortedIndices = sortByAspectRatio(batchCrops);

            double maxRatio = 0.0;
            for (int idx : sortedIndices) {
                Mat crop = batchCrops.get(idx);
                double ratio = crop.cols() / (double) Math.max(crop.rows(), 1);
                maxRatio = Math.max(maxRatio, ratio);
            }

            List<float[]> inputs = new ArrayList<>(batch);
            for (int idx : sortedIndices) {
                inputs.add(resizeAndNormalizeForRec(batchCrops.get(idx), maxRatio));
            }

            float[] batchData = concat(inputs);
            long[] shape = {batch, recImgC, recImgH, recImgW};
            try (OnnxTensor tensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(batchData), shape);
                 Result result = recSession.run(Collections.singletonMap(recInputName, tensor))) {
                float[][][] logits = (float[][][]) result.get(0).getValue();
                List<TextPrediction> batchPredictions = new ArrayList<>(logits.length);
                for (float[][] logit : logits) {
                    batchPredictions.add(decodeCtc(logit));
                }
                results.addAll(restoreOrder(batchPredictions, sortedIndices));
            }
        }
        return results;
    }

    private List<TextPrediction> restoreOrder(List<TextPrediction> predictions, int[] sortedIndices) {
        if (sortedIndices.length <= 1) {
            return new ArrayList<>(predictions);
        }
        TextPrediction[] restored = new TextPrediction[sortedIndices.length];
        for (int i = 0; i < sortedIndices.length; i++) {
            restored[sortedIndices[i]] = predictions.get(i);
        }
        return new ArrayList<>(Arrays.asList(restored));
    }

    private int argMax(float[] arr) {
        int maxIdx = 0;
        float maxVal = arr[0];
        for (int i = 1; i < arr.length; i++) {
            if (arr[i] > maxVal) {
                maxVal = arr[i];
                maxIdx = i;
            }
        }
        return maxIdx;
    }

    private ResizeInfo resizeForDetection(Mat src, int limitSideLen) {
        int h = src.rows();
        int w = src.cols();
        double ratio = 1.0;
        if (Math.max(h, w) > limitSideLen) {
            ratio = (double) limitSideLen / (double) Math.max(h, w);
        }
        int resizeH = (int) Math.round(h * ratio);
        int resizeW = (int) Math.round(w * ratio);
        resizeH = Math.max((resizeH / 32) * 32, 32);
        resizeW = Math.max((resizeW / 32) * 32, 32);
        Imgproc.resize(src, src, new Size(resizeW, resizeH), 0, 0, Imgproc.INTER_LINEAR);
        double ratioH = resizeH / (double) h;
        double ratioW = resizeW / (double) w;
        return new ResizeInfo(ratioH, ratioW);
    }

    private float[] normalizeToCHW(Mat image,
                                   float[] mean,
                                   float[] std,
                                   float scale) {
        int height = image.rows();
        int width = image.cols();
        int channels = image.channels();
        byte[] data = new byte[height * width * channels];
        image.get(0, 0, data);

        float[] result = new float[data.length];
        for (int i = 0; i < data.length; i++) {
            float value = (data[i] & 0xFF) * scale;
            result[i] = (value - mean[i % channels]) / std[i % channels];
        }

        float[] chw = new float[result.length];
        int channelSize = height * width;
        for (int c = 0; c < channels; c++) {
            for (int h = 0; h < height; h++) {
                for (int w = 0; w < width; w++) {
                    chw[c * channelSize + h * width + w] =
                            result[h * width * channels + w * channels + c];
                }
            }
        }
        return chw;
    }

    private List<Detection> postProcessDetection(float[][] map,
                                                 ResizeInfo resizeInfo,
                                                 Size originalSize) {
        Mat scores = new Mat(map.length, map[0].length, CvType.CV_32FC1);
        Mat binary = new Mat(map.length, map[0].length, CvType.CV_8UC1);
        for (int row = 0; row < map.length; row++) {
            for (int col = 0; col < map[row].length; col++) {
                scores.put(row, col, map[row][col]);
                binary.put(row, col, map[row][col] > detDbThresh ? 255 : 0);
            }
        }

        Imgproc.dilate(binary, binary, Mat.ones(2, 2, CvType.CV_8UC1));

        List<MatOfPoint> contours = new ArrayList<>();
        Imgproc.findContours(binary, contours, new Mat(), Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE);

        List<Detection> detections = new ArrayList<>();
        for (MatOfPoint contour : contours) {
            double score = boxScoreFast(scores, contour);
            if (score < detDbBoxThresh) {
                contour.release();
                continue;
            }
            MatOfPoint2f contour2f = new MatOfPoint2f(contour.toArray());
            RotatedRect rect = Imgproc.minAreaRect(contour2f);
            contour2f.release();
            if (Math.min(rect.size.height, rect.size.width) < detMinSize) {
                contour.release();
                continue;
            }
            Point[] rectPoints = new Point[4];
            rect.points(rectPoints);
            Point[] box = sortCorners(rectPoints);
            box = clipBox(box, scores.cols(), scores.rows());
            Point[] unclipped = unclipPolygon(box, detDbUnclipRatio);
            if (unclipped == null) {
                contour.release();
                continue;
            }
            MatOfPoint2f unclipped2f = new MatOfPoint2f(unclipped);
            RotatedRect newRect = Imgproc.minAreaRect(unclipped2f);
            unclipped2f.release();
            Point[] finalRectPoints = new Point[4];
            newRect.points(finalRectPoints);
            Point[] finalBox = sortCorners(finalRectPoints);
            double[][] reordered = toDetection(finalBox, resizeInfo, originalSize);
            detections.add(new Detection(reordered, score));
            contour.release();
        }
        scores.release();
        binary.release();
        return detections.stream()
                .sorted(Comparator.comparingDouble(d -> d.box[0][1]))
                .limit(detMaxCandidates)
                .collect(Collectors.toList());
    }

    private double boxScoreFast(Mat scores, MatOfPoint contour) {
        MatOfPoint2f contour2f = new MatOfPoint2f(contour.toArray());
        RotatedRect rect = Imgproc.minAreaRect(contour2f);
        contour2f.release();

        Point[] pts = new Point[4];
        rect.points(pts);

        Mat byteMask = Mat.zeros(scores.size(), CvType.CV_8UC1);
        MatOfPoint poly = new MatOfPoint(pts);
        Imgproc.fillPoly(byteMask, Collections.singletonList(poly), new Scalar(1));

        Mat floatMask = new Mat();
        byteMask.convertTo(floatMask, CvType.CV_32FC1);

        Scalar score = Core.sumElems(scores.mul(floatMask));
        double total = score.val[0];
        double count = Core.sumElems(floatMask).val[0];

        byteMask.release();
        floatMask.release();
        poly.release();
        return count == 0 ? 0 : total / count;
    }

    private Point[] sortCorners(Point[] pts) {
        Point[] result = new Point[4];
        Arrays.sort(pts, Comparator.comparingDouble(p -> p.x + p.y));
        Point leftTop = pts[0];
        Point rightBottom = pts[3];
        Point leftBottom;
        Point rightTop;
        if (pts[1].x < pts[2].x) {
            leftBottom = pts[1];
            rightTop = pts[2];
        } else {
            leftBottom = pts[2];
            rightTop = pts[1];
        }
        result[0] = leftTop;
        result[1] = rightTop;
        result[2] = rightBottom;
        result[3] = leftBottom;
        return result;
    }

    private double[][] toDetection(Point[] pts, ResizeInfo resizeInfo, Size originalSize) {
        double[][] box = new double[4][2];
        for (int i = 0; i < 4; i++) {
            box[i][0] = Math.min(Math.max(pts[i].x / resizeInfo.ratioW, 0), originalSize.width);
            box[i][1] = Math.min(Math.max(pts[i].y / resizeInfo.ratioH, 0), originalSize.height);
        }
        return orderClockwise(box);
    }

    private double[][] orderClockwise(double[][] box) {
        Point[] pts = new Point[box.length];
        for (int i = 0; i < box.length; i++) {
            pts[i] = new Point(box[i][0], box[i][1]);
        }
        Arrays.sort(pts, Comparator.comparingDouble(p -> p.x + p.y));
        Point leftTop = pts[0];
        Point rightBottom = pts[pts.length - 1];
        Point leftBottom;
        Point rightTop;
        if (pts[1].x < pts[2].x) {
            leftBottom = pts[1];
            rightTop = pts[2];
        } else {
            leftBottom = pts[2];
            rightTop = pts[1];
        }
        double[][] ordered = new double[4][2];
        ordered[0][0] = leftTop.x;
        ordered[0][1] = leftTop.y;
        ordered[1][0] = rightTop.x;
        ordered[1][1] = rightTop.y;
        ordered[2][0] = rightBottom.x;
        ordered[2][1] = rightBottom.y;
        ordered[3][0] = leftBottom.x;
        ordered[3][1] = leftBottom.y;
        return ordered;
    }

    private Point[] clipBox(Point[] box, int imgW, int imgH) {
        Point[] clipped = new Point[box.length];
        for (int i = 0; i < box.length; i++) {
            clipped[i] = new Point(
                    Math.max(0, Math.min(box[i].x, imgW - 1)),
                    Math.max(0, Math.min(box[i].y, imgH - 1)));
        }
        return clipped;
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
        dict.add(" ");
        return dict;
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

    /**
     * Immutable OCR result.
     */
    @Getter
    public static final class OcrResult {
        private final double[][] box;
        private final String text;
        private final double score;

        OcrResult(double[][] box, String text, double score) {
            this.box = Objects.requireNonNull(box);
            this.text = Objects.requireNonNull(text);
            this.score = score;
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

    private static final class TextPrediction {
        final String text;
        final double score;

        TextPrediction(String text, double score) {
            this.text = Objects.requireNonNull(text);
            this.score = score;
        }
    }
}
