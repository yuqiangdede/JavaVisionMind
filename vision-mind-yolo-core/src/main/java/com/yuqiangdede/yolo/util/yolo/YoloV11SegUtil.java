package com.yuqiangdede.yolo.util.yolo;

import ai.onnxruntime.*;
import com.yuqiangdede.yolo.config.Constant;
import com.yuqiangdede.yolo.dto.output.SegDetection;
import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.nio.FloatBuffer;
import java.util.*;
import java.util.List;

public class YoloV11SegUtil {

    public static final float NMS_THRESHOLD = 0.45f;
    public static final float MASK_THRESHOLD = 0.5f;


    static final Model yolomodel;
    private static OrtEnvironment environment;

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
        int numClasses = 80; // Assuming COCO dataset with 80 classes. Change if your model is different.

        return new Model(environment, session, netHeight, netWidth, numClasses);
    }

    static class Model {
        public OrtEnvironment env;
        public OrtSession session;
        public long netHeight;
        public long netWidth;
        public int numClasses;
        public Map<Integer, String> classNames;

        public Model(OrtEnvironment env, OrtSession session, long netHeight, long netWidth, int numClasses) {
            this.env = env;
            this.session = session;
            this.netHeight = netHeight;
            this.netWidth = netWidth;
            this.numClasses = numClasses;
            this.classNames = loadClassNames(); // Load COCO class names
        }

        private Map<Integer, String> loadClassNames() {
            Map<Integer, String> names = new HashMap<>();
            String[] cocoNames = new String[]{"person", "bicycle", "car", "motorcycle", "airplane", "bus", "train", "truck", "boat", "traffic light", "fire hydrant", "stop sign", "parking meter", "bench", "bird", "cat", "dog", "horse", "sheep", "cow", "elephant", "bear", "zebra", "giraffe", "backpack", "umbrella", "handbag", "tie", "suitcase", "frisbee", "skis", "snowboard", "sports ball", "kite", "baseball bat", "baseball glove", "skateboard", "surfboard", "tennis racket", "bottle", "wine glass", "cup", "fork", "knife", "spoon", "bowl", "banana", "apple", "sandwich", "orange", "broccoli", "carrot", "hot dog", "pizza", "donut", "cake", "chair", "couch", "potted plant", "bed", "dining table", "toilet", "tv", "laptop", "mouse", "remote", "keyboard", "cell phone", "microwave", "oven", "toaster", "sink", "refrigerator", "book", "clock", "vase", "scissors", "teddy bear", "hair drier", "toothbrush"};
            for (int i = 0; i < cocoNames.length; i++) {
                names.put(i, cocoNames[i]);
            }
            return names;
        }
    }


    public static List<SegDetection> predictor(Mat src, float threshold) throws OrtException {
        // Preprocessing and inference within a try-with-resources block
        // This ensures all ONNX resources (tensors, results) are closed automatically and correctly, preventing warnings.
        try (
                OnnxTensor tensor = transferTensor(src);
                OrtSession.Result result = yolomodel.session.run(Collections.singletonMap("images", tensor))
        ) {
            // The output tensors are part of the 'result' and will be closed with it.
            // We get them as OnnxValue.
            OnnxValue detectionsValue = result.get("output0")
                    .orElseThrow(()->new RuntimeException("Missing 'output0' in model outputs. Check your model's output names."));
            OnnxValue protosValue = result.get("output1")
                    .orElseThrow(()->new RuntimeException("Missing 'output1' in model outputs. Check your model's output names."));

            float[][][] detectionsRaw = (float[][][]) detectionsValue.getValue();
            float[][][][] protosRaw = (float[][][][]) protosValue.getValue();

            // Post-processing
            return processOutputs(detectionsRaw, protosRaw, src, threshold);
        } // All resources (tensor, result, and its contained tensors) are closed here automatically.
    }

    private static List<SegDetection> processOutputs(float[][][] detectionsRaw, float[][][][] protosRaw, Mat image, float threshold) {
        // Constants from model output shapes
        int maskProtoChannels = protosRaw[0].length;
        int maskProtoHeight = protosRaw[0][0].length;
        int maskProtoWidth = protosRaw[0][0][0].length;
        int numProposals = detectionsRaw[0][0].length;
        int detectionSize = detectionsRaw[0].length; // 4 (box) + num_classes + mask_coeffs
        int numMaskCoeffs = detectionSize - yolomodel.numClasses - 4;

        Mat protos = new Mat(maskProtoChannels, maskProtoHeight * maskProtoWidth, CvType.CV_32F);
        float[] protosData = new float[maskProtoChannels * maskProtoHeight * maskProtoWidth];
        for (int i = 0; i < maskProtoChannels; i++) {
            for (int j = 0; j < maskProtoHeight; j++) {
                for (int k = 0; k < maskProtoWidth; k++) {
                    protosData[i * (maskProtoHeight * maskProtoWidth) + j * maskProtoWidth + k] = protosRaw[0][i][j][k];
                }
            }
        }
        protos.put(0, 0, protosData);

        List<Rect> boxes = new ArrayList<>();
        List<Float> scores = new ArrayList<>();
        List<Integer> classIds = new ArrayList<>();
        List<Mat> maskCoeffsList = new ArrayList<>();

        float scaleX = (float) image.cols() / yolomodel.netWidth;
        float scaleY = (float) image.rows() / yolomodel.netHeight;

        // Transpose detection data for easier processing: from [1][116][8400] to [8400][116]
        float[][] transposedDetections = new float[numProposals][detectionSize];
        for (int i = 0; i < detectionSize; i++) {
            for (int j = 0; j < numProposals; j++) {
                transposedDetections[j][i] = detectionsRaw[0][i][j];
            }
        }

        for (int i = 0; i < numProposals; i++) {
            float[] detection = transposedDetections[i];
            float[] classScores = Arrays.copyOfRange(detection, 4, 4 + yolomodel.numClasses);

            int classId = -1;
            float maxScore = 0f;
            for (int j = 0; j < classScores.length; j++) {
                if (classScores[j] > maxScore) {
                    maxScore = classScores[j];
                    classId = j;
                }
            }

            if (maxScore > threshold) {
                scores.add(maxScore);
                classIds.add(classId);

                float cx = detection[0];
                float cy = detection[1];
                float w = detection[2];
                float h = detection[3];

                int left = (int) ((cx - 0.5 * w) * scaleX);
                int top = (int) ((cy - 0.5 * h) * scaleY);
                int width = (int) (w * scaleX);
                int height = (int) (h * scaleY);
                boxes.add(new Rect(left, top, width, height));

                float[] coeffs = Arrays.copyOfRange(detection, 4 + yolomodel.numClasses, detectionSize);
                Mat coeffsMat = new Mat(1, numMaskCoeffs, CvType.CV_32F);
                coeffsMat.put(0, 0, coeffs);
                maskCoeffsList.add(coeffsMat);
            }
        }

        // NMS (manual implementation)
        List<Integer> keepIndices = new ArrayList<>();
        if (!boxes.isEmpty()) {
            // Create a list of indices to sort by score
            List<Integer> sortedIndices = new ArrayList<>();
            for (int i = 0; i < scores.size(); i++) {
                sortedIndices.add(i);
            }
            sortedIndices.sort((a, b)->Float.compare(scores.get(b), scores.get(a)));

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
                    // Check for same class
                    if (classIds.get(idx).equals(classIds.get(nextIdx))) {
                        float iou = calculateIoU(boxes.get(idx), boxes.get(nextIdx));
                        if (iou > NMS_THRESHOLD) {
                            suppressed[nextIdx] = true;
                        }
                    }
                }
            }
        }

        List<SegDetection> finalDetections = new ArrayList<>();
        for (int idx : keepIndices) {
            Rect box = boxes.get(idx);
            Mat maskCoeffs = maskCoeffsList.get(idx);

            // Generate mask
            Mat finalMask = generateMask(maskCoeffs, protos, box, image.size(), maskProtoHeight, maskProtoWidth);

            finalDetections.add(new SegDetection(box, scores.get(idx), classIds.get(idx), finalMask));
        }

        return finalDetections;
    }

    private static Mat generateMask(Mat coeffs, Mat protos, Rect box, Size imageSize, int maskProtoHeight, int maskProtoWidth) {
        Mat matMulResult = new Mat();
        Core.gemm(coeffs, protos, 1, new Mat(), 0, matMulResult); // Matrix multiplication

        // Sigmoid: 1 / (1 + exp(-x))
        Core.multiply(matMulResult, new Scalar(-1), matMulResult); // matMulResult becomes -x
        Core.exp(matMulResult, matMulResult); // matMulResult becomes exp(-x)
        Core.add(matMulResult, new Scalar(1), matMulResult); // matMulResult becomes 1 + exp(-x)
        Core.divide(1, matMulResult, matMulResult); // matMulResult becomes 1 / (1 + exp(-x))
        matMulResult = matMulResult.reshape(1, maskProtoHeight);

        // Resize mask from proto size to image size
        Mat resizedMask = new Mat();
        Imgproc.resize(matMulResult, resizedMask, imageSize, 0, 0, Imgproc.INTER_LINEAR);

        // Clip the bounding box to the image dimensions to prevent out-of-bounds errors
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

        // Ensure the box has a valid area before cropping
        if (clippedBox.width <= 0 || clippedBox.height <= 0) {
            return Mat.zeros(imageSize, CvType.CV_8U);
        }

        // Crop mask with the clipped bounding box
        Mat croppedMask = new Mat(resizedMask, clippedBox);

        // Binarize the mask
        Mat binaryMask = new Mat();
        Imgproc.threshold(croppedMask, binaryMask, MASK_THRESHOLD, 255, Imgproc.THRESH_BINARY);

        // Place the binarized mask onto a full-size black canvas
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

    static OnnxTensor transferTensor(Mat src) throws OrtException {
        Mat dst = new Mat();
        Imgproc.resize(src, dst, new Size(yolomodel.netWidth, yolomodel.netHeight));
        Imgproc.cvtColor(dst, dst, Imgproc.COLOR_BGR2RGB);
        dst.convertTo(dst, CvType.CV_32FC3, 1. / 255);

        float[] whc = new float[(int) (3 * yolomodel.netWidth * yolomodel.netHeight)];
        dst.get(0, 0, whc);
        float[] chw = whc2cwh(whc);

        return OnnxTensor.createTensor(yolomodel.env, FloatBuffer.wrap(chw), new long[]{1, 3, yolomodel.netHeight, yolomodel.netWidth});
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

    public static BufferedImage drawImage(BufferedImage image, List<SegDetection> detections) {
        // 将BufferedImage转换为Mat对象
        Mat imageMat = bufferedImageToMat(image);
        // 生成颜色列表
        List<Scalar> colors = generateColors(yolomodel.numClasses);


        for (SegDetection det : detections) {
            // 获取对应的颜色
            Scalar color = colors.get(det.classId);

            // 1. 从掩码中查找轮廓
            // 创建存储轮廓的列表
            List<MatOfPoint> contours = new ArrayList<>();
            // 创建存储轮廓层次结构的Mat对象
            Mat hierarchy = new Mat();
            // 查找轮廓
            Imgproc.findContours(det.mask, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

            // 2. 绘制轮廓线
            // 绘制轮廓
            Imgproc.drawContours(imageMat, contours, -1, color, 2);


//            // 3. 为填充创建彩色覆盖层
//            // 创建与imageMat相同大小的彩色Mat对象
//            Mat coloredMask = new Mat(imageMat.size(), imageMat.type(), color);
//            // 创建用于存储转换为BGR格式的掩码的Mat对象
//            Mat mask3channel = new Mat();
//            // 将掩码从灰度转换为BGR格式
//            Imgproc.cvtColor(det.mask, mask3channel, Imgproc.COLOR_GRAY2BGR);
//
//
//            // 4. 将彩色填充与原始图像混合，使其半透明
//            // 创建用于存储混合结果的Mat对象
//            Mat maskedOverlay = new Mat();
//            // 执行按位与操作，得到彩色填充与掩码的交集
//            Core.bitwise_and(coloredMask, mask3channel, maskedOverlay);
//            // 将混合结果加权添加到原始图像上
//            Core.addWeighted(imageMat, 1.0, maskedOverlay, 0.5, 0, imageMat);


            // 5. 在没有实色背景的情况下绘制标签
            // 获取类别名称和得分，并格式化为字符串
//            String label = yolomodel.classNames.getOrDefault(det.classId, "Unknown") + " " + String.format("%.2f", det.score);
//            // 在图像上绘制标签
//            Imgproc.putText(imageMat, label, new Point(det.box.x, det.box.y - 10),
//                    Imgproc.FONT_HERSHEY_SIMPLEX, 0.7, color, 2);
        }


        // 将Mat对象转换回BufferedImage
        return matToBufferedImage(imageMat);
    }


    private static List<Scalar> generateColors(int numColors) {
        List<Scalar> colors = new ArrayList<>();
        Random random = new Random(42); // Seed for consistent colors
        for (int i = 0; i < numColors; i++) {
            int r = random.nextInt(256);
            int g = random.nextInt(256);
            int b = random.nextInt(256);
            colors.add(new Scalar(b, g, r));
        }
        return colors;
    }

    public static BufferedImage matToBufferedImage(Mat matrix) {
        int type = BufferedImage.TYPE_BYTE_GRAY;
        if (matrix.channels() > 1) {
            Imgproc.cvtColor(matrix, matrix, Imgproc.COLOR_BGR2RGB);
            type = BufferedImage.TYPE_3BYTE_BGR;
        }
        int bufferSize = matrix.channels() * matrix.cols() * matrix.rows();
        byte[] b = new byte[bufferSize];
        matrix.get(0, 0, b);
        BufferedImage image = new BufferedImage(matrix.cols(), matrix.rows(), type);
        final byte[] targetPixels = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();
        System.arraycopy(b, 0, targetPixels, 0, b.length);
        return image;
    }

    public static Mat bufferedImageToMat(BufferedImage bi) {
        Mat mat = new Mat(bi.getHeight(), bi.getWidth(), CvType.CV_8UC3);
        byte[] data = ((DataBufferByte) bi.getRaster().getDataBuffer()).getData();
        mat.put(0, 0, data);
        Imgproc.cvtColor(mat, mat, Imgproc.COLOR_RGB2BGR);
        return mat;
    }
}
