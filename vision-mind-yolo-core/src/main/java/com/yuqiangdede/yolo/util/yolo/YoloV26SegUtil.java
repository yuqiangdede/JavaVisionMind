package com.yuqiangdede.yolo.util.yolo;

import ai.onnxruntime.NodeInfo;
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OnnxValue;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import ai.onnxruntime.TensorInfo;
import com.yuqiangdede.yolo.config.Constant;
import com.yuqiangdede.yolo.dto.output.SegDetection;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class YoloV26SegUtil {

    private static final float NMS_THRESHOLD = 0.45f;
    private static final float MASK_THRESHOLD = 0.5f;

    static final Model yolomodel;
    private static final OrtEnvironment environment;

    static {
        try {
            environment = OrtEnvironment.getEnvironment();
            yolomodel = load();
        } catch (OrtException e) {
            throw new RuntimeException(e);
        }
    }

    private static Model load() throws OrtException {
        OrtSession session = environment.createSession(Constant.YOLO_SEG_ONNX_PATH, new OrtSession.SessionOptions());
        Map<String, NodeInfo> infoMap = session.getInputInfo();
        TensorInfo nodeInfo = (TensorInfo) infoMap.get("images").getInfo();
        long netHeight = nodeInfo.getShape()[2];
        long netWidth = nodeInfo.getShape()[3];
        return new Model(environment, session, netHeight, netWidth);
    }

    public static List<SegDetection> predictor(Mat src, float threshold) throws OrtException {
        try (OnnxTensor tensor = transferTensor(src)) {
            try (OrtSession.Result result = yolomodel.session.run(Collections.singletonMap("images", tensor))) {
                OnnxValue output0 = result.get("output0")
                        .orElseThrow(() -> new RuntimeException("Missing 'output0' in model outputs."));
                OnnxValue output1 = result.get("output1")
                        .orElseThrow(() -> new RuntimeException("Missing 'output1' in model outputs."));
                float[][] detections = ((float[][][]) output0.getValue())[0];
                float[][][] protos = ((float[][][][]) output1.getValue())[0];
                return processOutputs(detections, protos, src, threshold);
            }
        }
    }

    private static List<SegDetection> processOutputs(float[][] detections, float[][][] protos, Mat image, float threshold) {
        int maskProtoChannels = protos.length;
        int maskProtoHeight = protos[0].length;
        int maskProtoWidth = protos[0][0].length;
        int numMaskCoeffs = maskProtoChannels;

        Mat protosMat = new Mat(maskProtoChannels, maskProtoHeight * maskProtoWidth, CvType.CV_32F);
        float[] protosData = new float[maskProtoChannels * maskProtoHeight * maskProtoWidth];
        for (int i = 0; i < maskProtoChannels; i++) {
            for (int j = 0; j < maskProtoHeight; j++) {
                for (int k = 0; k < maskProtoWidth; k++) {
                    protosData[i * (maskProtoHeight * maskProtoWidth) + j * maskProtoWidth + k] = protos[i][j][k];
                }
            }
        }
        protosMat.put(0, 0, protosData);

        List<Rect> boxes = new ArrayList<>();
        List<Float> scores = new ArrayList<>();
        List<Integer> classIds = new ArrayList<>();
        List<Mat> maskCoeffsList = new ArrayList<>();

        float scaleX = (float) image.cols() / yolomodel.netWidth;
        float scaleY = (float) image.rows() / yolomodel.netHeight;

        for (float[] row : detections) {
            float score = row[4];
            if (score <= threshold) {
                continue;
            }
            float x1 = row[0] * scaleX;
            float y1 = row[1] * scaleY;
            float x2 = row[2] * scaleX;
            float y2 = row[3] * scaleY;
            int left = Math.max(0, Math.round(x1));
            int top = Math.max(0, Math.round(y1));
            int width = Math.max(0, Math.round(x2 - x1));
            int height = Math.max(0, Math.round(y2 - y1));
            boxes.add(new Rect(left, top, width, height));
            scores.add(score);
            classIds.add(Math.round(row[5]));

            float[] coeffs = Arrays.copyOfRange(row, 6, 6 + numMaskCoeffs);
            Mat coeffsMat = new Mat(1, numMaskCoeffs, CvType.CV_32F);
            coeffsMat.put(0, 0, coeffs);
            maskCoeffsList.add(coeffsMat);
        }

        List<Integer> keepIndices = new ArrayList<>();
        if (!boxes.isEmpty()) {
            if (!Constant.YOLO_SEG_NMS_ENABLED) {
                for (int i = 0; i < boxes.size(); i++) {
                    keepIndices.add(i);
                }
            } else {
                List<Integer> sortedIndices = new ArrayList<>();
                for (int i = 0; i < scores.size(); i++) {
                    sortedIndices.add(i);
                }
                sortedIndices.sort((a, b) -> Float.compare(scores.get(b), scores.get(a)));

                boolean[] suppressed = new boolean[boxes.size()];
                for (int i = 0; i < sortedIndices.size(); i++) {
                    int idx = sortedIndices.get(i);
                    if (suppressed[idx]) {
                        continue;
                    }
                    keepIndices.add(idx);
                    for (int j = i + 1; j < sortedIndices.size(); j++) {
                        int nextIdx = sortedIndices.get(j);
                        if (suppressed[nextIdx]) {
                            continue;
                        }
                        if (classIds.get(idx).equals(classIds.get(nextIdx))) {
                            float iou = calculateIoU(boxes.get(idx), boxes.get(nextIdx));
                            if (iou > NMS_THRESHOLD) {
                                suppressed[nextIdx] = true;
                            }
                        }
                    }
                }
            }
        }

        List<SegDetection> finalDetections = new ArrayList<>();
        for (int idx : keepIndices) {
            Rect box = boxes.get(idx);
            Mat maskCoeffs = maskCoeffsList.get(idx);
            Mat finalMask = generateMask(maskCoeffs, protosMat, box, image.size(), maskProtoHeight, maskProtoWidth);
            finalDetections.add(new SegDetection(box, scores.get(idx), classIds.get(idx), finalMask));
        }

        return finalDetections;
    }

    private static Mat generateMask(Mat coeffs, Mat protos, Rect box, Size imageSize, int maskProtoHeight, int maskProtoWidth) {
        Mat matMulResult = new Mat();
        Core.gemm(coeffs, protos, 1, new Mat(), 0, matMulResult);

        Core.multiply(matMulResult, new Scalar(-1), matMulResult);
        Core.exp(matMulResult, matMulResult);
        Core.add(matMulResult, new Scalar(1), matMulResult);
        Core.divide(1, matMulResult, matMulResult);
        matMulResult = matMulResult.reshape(1, maskProtoHeight);

        Mat resizedMask = new Mat();
        Imgproc.resize(matMulResult, resizedMask, imageSize, 0, 0, Imgproc.INTER_LINEAR);

        Rect clippedBox = new Rect(
                Math.max(0, box.x),
                Math.max(0, box.y),
                box.width,
                box.height
        );
        if (clippedBox.x + clippedBox.width > imageSize.width) {
            clippedBox.width = (int) (imageSize.width - clippedBox.x);
        }
        if (clippedBox.y + clippedBox.height > imageSize.height) {
            clippedBox.height = (int) (imageSize.height - clippedBox.y);
        }

        if (clippedBox.width <= 0 || clippedBox.height <= 0) {
            return Mat.zeros(imageSize, CvType.CV_8U);
        }

        Mat croppedMask = new Mat(resizedMask, clippedBox);
        Mat binaryMask = new Mat();
        Imgproc.threshold(croppedMask, binaryMask, MASK_THRESHOLD, 255, Imgproc.THRESH_BINARY);

        Mat fullMask = Mat.zeros(imageSize, CvType.CV_8U);
        binaryMask.convertTo(binaryMask, CvType.CV_8U);
        binaryMask.copyTo(new Mat(fullMask, clippedBox));

        return fullMask;
    }

    private static float calculateIoU(Rect box1, Rect box2) {
        int x1 = Math.max(box1.x, box2.x);
        int y1 = Math.max(box1.y, box2.y);
        int x2 = Math.min(box1.x + box1.width, box2.x + box2.width);
        int y2 = Math.min(box1.y + box1.height, box2.y + box2.height);

        int intersectionArea = Math.max(0, x2 - x1) * Math.max(0, y2 - y1);
        int box1Area = box1.width * box1.height;
        int box2Area = box2.width * box2.height;
        float unionArea = box1Area + box2Area - intersectionArea;

        if (unionArea == 0) {
            return 0f;
        }

        return intersectionArea / unionArea;
    }

    private static OnnxTensor transferTensor(Mat src) throws OrtException {
        Mat dst = new Mat();
        Imgproc.resize(src, dst, new Size(yolomodel.netWidth, yolomodel.netHeight));
        Imgproc.cvtColor(dst, dst, Imgproc.COLOR_BGR2RGB);
        dst.convertTo(dst, CvType.CV_32FC3, 1. / 255);

        float[] whc = new float[(int) (3 * yolomodel.netWidth * yolomodel.netHeight)];
        dst.get(0, 0, whc);
        float[] chw = whc2cwh(whc);

        return OnnxTensor.createTensor(yolomodel.env, FloatBuffer.wrap(chw),
                new long[]{1, 3, yolomodel.netHeight, yolomodel.netWidth});
    }

    private static float[] whc2cwh(float[] src) {
        float[] chw = new float[src.length];
        int j = 0;
        for (int ch = 0; ch < 3; ++ch) {
            for (int i = ch; i < src.length; i += 3) {
                chw[j] = src[i];
                j++;
            }
        }
        return chw;
    }

    static class Model {
        public OrtEnvironment env;
        public OrtSession session;
        public long netHeight;
        public long netWidth;

        public Model(OrtEnvironment env, OrtSession session, long netHeight, long netWidth) {
            this.env = env;
            this.session = session;
            this.netHeight = netHeight;
            this.netWidth = netWidth;
        }
    }
}
