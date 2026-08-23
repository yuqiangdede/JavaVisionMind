package com.yuqiangdede.yolo.util.yolo;

import ai.onnxruntime.NodeInfo;
import ai.onnxruntime.OnnxJavaType;
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OnnxValue;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import ai.onnxruntime.TensorInfo;
import com.yuqiangdede.yolo.dto.output.DepthEstimationResult;
import lombok.extern.slf4j.Slf4j;

import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * YOLO26 深度估计 ONNX Runtime 适配器。
 *
 * <p>模型输出已经包含正值变换和尺度标定，数值单位为米。这里仅负责 letterbox 的逆变换，
 * 不执行 sigmoid、指数变换、置信度过滤或 NMS。</p>
 */
@Slf4j
public class YoloV26DepthUtil implements AutoCloseable {

    static final int DEFAULT_INPUT_SIZE = 768;
    private static final int RGB_CHANNELS = 3;
    private static final int MAX_MODEL_INPUT_PIXELS = 4_000_000;
    private static final float LETTERBOX_VALUE = 114f / 255f;
    private static final Pattern INTEGER_PATTERN = Pattern.compile("\\d+");
    private static final OrtEnvironment ENVIRONMENT = OrtEnvironment.getEnvironment();

    private final Path modelPath;
    private final ReentrantReadWriteLock lifecycleLock = new ReentrantReadWriteLock();
    private volatile Model model;
    private boolean closed;

    public YoloV26DepthUtil(Path modelPath) {
        this.modelPath = modelPath.toAbsolutePath().normalize();
    }

    /**
     * 对一张图片执行深度估计。
     *
     * @param image 原始 RGB 图片
     * @return 与原图尺寸一致的米制深度图及统计信息
     * @throws OrtException ONNX Runtime 推理异常
     */
    public DepthEstimationResult estimate(BufferedImage image) throws OrtException {
        if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0) {
            throw new IllegalArgumentException("image is null or empty");
        }

        lifecycleLock.readLock().lock();
        try {
            if (closed) {
                throw new IllegalStateException("YOLO26 depth model is closed");
            }
            Model activeModel = getModel();
            LetterboxInput input = preprocess(image, activeModel.inputWidth(), activeModel.inputHeight());
            long[] shape = {1, RGB_CHANNELS, activeModel.inputHeight(), activeModel.inputWidth()};

            input.chw().rewind();
            try (OnnxTensor tensor = OnnxTensor.createTensor(ENVIRONMENT, input.chw(), shape)) {
                Map<String, OnnxTensor> inputs = new LinkedHashMap<>();
                inputs.put(activeModel.inputName(), tensor);
                try (OrtSession.Result outputs = activeModel.session().run(inputs)) {
                    OnnxValue output = outputs.get(activeModel.outputName())
                            .orElseThrow(() -> new IllegalStateException(
                                    "Depth model output is missing: " + activeModel.outputName()));
                    float[][] modelDepth = extractDepthMap(output);
                    float[][] restoredDepth = restoreDepthMap(modelDepth, input);
                    return buildResult(restoredDepth);
                }
            }
        } finally {
            lifecycleLock.readLock().unlock();
        }
    }

    /**
     * 生成深度预览图。disparity 模式使用逆深度分位数着色；metric 模式使用固定米制范围。
     */
    public BufferedImage renderPreview(BufferedImage source,
                                       DepthEstimationResult result,
                                       String visualizationMode,
                                       Float minDepth,
                                       Float maxDepth) {
        if (source.getWidth() != result.getWidth() || source.getHeight() != result.getHeight()) {
            throw new IllegalArgumentException("source image and depth map dimensions do not match");
        }

        String mode = visualizationMode == null || visualizationMode.isBlank()
                ? "disparity" : visualizationMode.trim().toLowerCase();
        float lower;
        float upper;
        boolean inverseDepth;
        if ("disparity".equals(mode)) {
            float[] inverseValues = validValues(result.getDepthMap(), true);
            lower = percentile(inverseValues, 0.02f);
            upper = percentile(inverseValues, 0.98f);
            inverseDepth = true;
        } else if ("metric".equals(mode)) {
            if (minDepth == null || maxDepth == null || !Float.isFinite(minDepth)
                    || !Float.isFinite(maxDepth) || minDepth < 0 || maxDepth <= minDepth) {
                throw new IllegalArgumentException(
                        "metric visualization requires finite minDepth and maxDepth with 0 <= minDepth < maxDepth");
            }
            lower = minDepth;
            upper = maxDepth;
            inverseDepth = false;
        } else {
            throw new IllegalArgumentException("unsupported visualizationMode: " + visualizationMode);
        }

        if (!(upper > lower)) {
            upper = lower + Math.max(Math.ulp(lower), 1.0e-6f);
        }

        BufferedImage preview = new BufferedImage(result.getWidth(), result.getHeight(), BufferedImage.TYPE_INT_RGB);
        float[] depths = result.getDepthMap();
        for (int y = 0; y < result.getHeight(); y++) {
            for (int x = 0; x < result.getWidth(); x++) {
                int index = y * result.getWidth() + x;
                float depth = depths[index];
                if (!Float.isFinite(depth) || depth <= 0) {
                    preview.setRGB(x, y, 0);
                    continue;
                }
                float value = inverseDepth ? 1f / depth : depth;
                float normalized = clamp((value - lower) / (upper - lower), 0f, 1f);
                int heatRgb = jetColor(normalized);
                preview.setRGB(x, y, blend(source.getRGB(x, y), heatRgb, 0.4f, 0.6f));
            }
        }
        return preview;
    }

    private Model getModel() throws OrtException {
        Model current = model;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            if (model == null) {
                model = loadModel();
            }
            return model;
        }
    }

    /**
     * 关闭延迟创建的 ONNX Runtime 会话。
     */
    @Override
    public void close() throws OrtException {
        lifecycleLock.writeLock().lock();
        try {
            closed = true;
            Model current = model;
            model = null;
            if (current != null) {
                current.session().close();
            }
        } finally {
            lifecycleLock.writeLock().unlock();
        }
    }

    private Model loadModel() throws OrtException {
        if (!Files.isRegularFile(modelPath)) {
            throw new IllegalStateException("YOLO26 depth model not found: " + modelPath);
        }

        OrtSession session;
        try (OrtSession.SessionOptions options = new OrtSession.SessionOptions()) {
            session = ENVIRONMENT.createSession(modelPath.toString(), options);
        }

        try {
            Map<String, NodeInfo> inputInfo = session.getInputInfo();
            String inputName = selectNodeName(inputInfo, "images", "input");
            TensorInfo inputTensorInfo = requireTensorInfo(inputInfo.get(inputName), "input", inputName);
            requireFloatTensor(inputTensorInfo, "input", inputName);
            long[] inputShape = inputTensorInfo.getShape();
            if (inputShape.length != 4) {
                throw new IllegalStateException("Depth model input must be NCHW rank 4, actual rank: "
                        + inputShape.length);
            }
            if (inputShape[0] > 0 && inputShape[0] != 1) {
                throw new IllegalStateException("Depth model input batch size must be 1, actual: " + inputShape[0]);
            }
            if (inputShape[1] > 0 && inputShape[1] != RGB_CHANNELS) {
                throw new IllegalStateException("Depth model input channel count must be 3, actual: " + inputShape[1]);
            }

            int[] metadataSize = metadataImageSize(session);
            int inputHeight = resolveDimension(inputShape[2], metadataSize[0]);
            int inputWidth = resolveDimension(inputShape[3], metadataSize[1]);

            Map<String, NodeInfo> outputInfo = session.getOutputInfo();
            String outputName = selectNodeName(outputInfo, "output0", "output");
            TensorInfo outputTensorInfo = requireTensorInfo(outputInfo.get(outputName), "output", outputName);
            requireFloatTensor(outputTensorInfo, "output", outputName);
            long[] outputShape = outputTensorInfo.getShape();
            int outputRank = outputShape.length;
            if (outputRank != 3 && outputRank != 4) {
                throw new IllegalStateException("Depth model output must have rank 3 or 4, actual rank: " + outputRank);
            }
            if (outputShape[0] > 0 && outputShape[0] != 1) {
                throw new IllegalStateException("Depth model output batch size must be 1, actual: " + outputShape[0]);
            }
            if (outputRank == 4 && outputShape[1] > 0 && outputShape[1] != 1) {
                throw new IllegalStateException("Depth model output channel count must be 1, actual: " + outputShape[1]);
            }

            String task = session.getMetadata().getCustomMetadata().get("task");
            if (task != null && !task.isBlank() && !"depth".equalsIgnoreCase(task)) {
                throw new IllegalStateException("ONNX model task is not depth: " + task);
            }

            log.info("Loaded YOLO26 depth model: path={}, input={} [1,3,{},{}], output={}",
                    modelPath, inputName, inputHeight, inputWidth, outputName);
            return new Model(session, inputName, outputName, inputWidth, inputHeight);
        } catch (OrtException | RuntimeException ex) {
            session.close();
            throw ex;
        }
    }

    private static TensorInfo requireTensorInfo(NodeInfo nodeInfo, String kind, String name) {
        if (nodeInfo == null || !(nodeInfo.getInfo() instanceof TensorInfo tensorInfo)) {
            throw new IllegalStateException("Depth model " + kind + " is not a tensor: " + name);
        }
        return tensorInfo;
    }

    private static void requireFloatTensor(TensorInfo tensorInfo, String kind, String name) {
        if (tensorInfo.type != OnnxJavaType.FLOAT) {
            throw new IllegalStateException("Depth model " + kind + " must be float32: "
                    + name + " is " + tensorInfo.type);
        }
    }

    private static String selectNodeName(Map<String, NodeInfo> nodes, String preferredName, String kind) {
        if (nodes.containsKey(preferredName)) {
            return preferredName;
        }
        if (nodes.size() == 1) {
            return nodes.keySet().iterator().next();
        }
        throw new IllegalStateException("Unable to select depth model " + kind + " from: " + nodes.keySet());
    }

    private static int[] metadataImageSize(OrtSession session) throws OrtException {
        String imageSize = session.getMetadata().getCustomMetadata().get("imgsz");
        if (imageSize == null || imageSize.isBlank()) {
            return new int[]{DEFAULT_INPUT_SIZE, DEFAULT_INPUT_SIZE};
        }

        Matcher matcher = INTEGER_PATTERN.matcher(imageSize);
        int[] values = new int[2];
        int count = 0;
        while (matcher.find() && count < values.length) {
            values[count++] = Integer.parseInt(matcher.group());
        }
        if (count == 1) {
            return new int[]{values[0], values[0]};
        }
        if (count == 2) {
            return values;
        }
        return new int[]{DEFAULT_INPUT_SIZE, DEFAULT_INPUT_SIZE};
    }

    private static int resolveDimension(long tensorDimension, int metadataDimension) {
        long resolved = tensorDimension > 0 ? tensorDimension : metadataDimension;
        if (resolved <= 0 || resolved > Integer.MAX_VALUE) {
            throw new IllegalStateException("Invalid depth model input dimension: " + resolved);
        }
        return (int) resolved;
    }

    static LetterboxInput preprocess(BufferedImage image, int inputWidth, int inputHeight) {
        double scale = Math.min((double) inputWidth / image.getWidth(), (double) inputHeight / image.getHeight());
        int resizedWidth = Math.max(1, (int) Math.rint(image.getWidth() * scale));
        int resizedHeight = Math.max(1, (int) Math.rint(image.getHeight() * scale));
        int horizontalPadding = inputWidth - resizedWidth;
        int verticalPadding = inputHeight - resizedHeight;
        int left = Math.max(0, Math.round(horizontalPadding / 2f - 0.1f));
        int top = Math.max(0, Math.round(verticalPadding / 2f - 0.1f));
        int right = Math.max(0, inputWidth - resizedWidth - left);
        int bottom = Math.max(0, inputHeight - resizedHeight - top);

        int planeSize = Math.multiplyExact(inputWidth, inputHeight);
        if (planeSize > MAX_MODEL_INPUT_PIXELS) {
            throw new IllegalStateException("Depth model input is too large: " + inputWidth + "x" + inputHeight);
        }
        int tensorValues = Math.multiplyExact(RGB_CHANNELS, planeSize);
        int tensorBytes = Math.multiplyExact(tensorValues, Float.BYTES);
        FloatBuffer chw = ByteBuffer.allocateDirect(tensorBytes)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        for (int index = 0; index < chw.capacity(); index++) {
            chw.put(index, LETTERBOX_VALUE);
        }
        for (int y = 0; y < resizedHeight; y++) {
            double sourceY = sourceCoordinate(y, image.getHeight(), resizedHeight);
            int y0 = (int) Math.floor(sourceY);
            int y1 = Math.min(y0 + 1, image.getHeight() - 1);
            double yWeight = sourceY - y0;
            for (int x = 0; x < resizedWidth; x++) {
                double sourceX = sourceCoordinate(x, image.getWidth(), resizedWidth);
                int x0 = (int) Math.floor(sourceX);
                int x1 = Math.min(x0 + 1, image.getWidth() - 1);
                double xWeight = sourceX - x0;

                int topLeft = image.getRGB(x0, y0);
                int topRight = image.getRGB(x1, y0);
                int bottomLeft = image.getRGB(x0, y1);
                int bottomRight = image.getRGB(x1, y1);
                int outputIndex = (top + y) * inputWidth + left + x;
                chw.put(outputIndex, interpolateColor(
                        topLeft, topRight, bottomLeft, bottomRight, 16, xWeight, yWeight) / 255f);
                chw.put(planeSize + outputIndex, interpolateColor(
                        topLeft, topRight, bottomLeft, bottomRight, 8, xWeight, yWeight) / 255f);
                chw.put(2 * planeSize + outputIndex, interpolateColor(
                        topLeft, topRight, bottomLeft, bottomRight, 0, xWeight, yWeight) / 255f);
            }
        }
        return new LetterboxInput(chw, image.getWidth(), image.getHeight(), inputWidth, inputHeight,
                left, top, right, bottom);
    }

    private static double sourceCoordinate(int destinationIndex, int sourceSize, int destinationSize) {
        double coordinate = (destinationIndex + 0.5d) * sourceSize / destinationSize - 0.5d;
        return Math.max(0d, Math.min(sourceSize - 1d, coordinate));
    }

    private static float interpolateColor(int topLeft,
                                          int topRight,
                                          int bottomLeft,
                                          int bottomRight,
                                          int shift,
                                          double xWeight,
                                          double yWeight) {
        int c00 = topLeft >> shift & 0xff;
        int c01 = topRight >> shift & 0xff;
        int c10 = bottomLeft >> shift & 0xff;
        int c11 = bottomRight >> shift & 0xff;
        double top = c00 + (c01 - c00) * xWeight;
        double bottom = c10 + (c11 - c10) * xWeight;
        return Math.round((float) (top + (bottom - top) * yWeight));
    }

    static float[][] restoreDepthMap(float[][] modelDepth, LetterboxInput input) {
        validateRectangularMap(modelDepth);
        float[][] inputSizedDepth = modelDepth;
        if (modelDepth.length != input.inputHeight() || modelDepth[0].length != input.inputWidth()) {
            inputSizedDepth = resizeDepthMap(modelDepth, input.inputWidth(), input.inputHeight());
        }

        int croppedWidth = input.inputWidth() - input.left() - input.right();
        int croppedHeight = input.inputHeight() - input.top() - input.bottom();
        if (croppedWidth <= 0 || croppedHeight <= 0) {
            throw new IllegalStateException("Invalid letterbox crop dimensions");
        }
        float[][] cropped = new float[croppedHeight][croppedWidth];
        for (int y = 0; y < croppedHeight; y++) {
            System.arraycopy(inputSizedDepth[input.top() + y], input.left(), cropped[y], 0, croppedWidth);
        }
        return resizeDepthMap(cropped, input.originalWidth(), input.originalHeight());
    }

    static float[][] resizeDepthMap(float[][] source, int targetWidth, int targetHeight) {
        validateRectangularMap(source);
        if (targetWidth <= 0 || targetHeight <= 0) {
            throw new IllegalArgumentException("target dimensions must be positive");
        }
        if (source.length == targetHeight && source[0].length == targetWidth) {
            float[][] copy = new float[targetHeight][targetWidth];
            for (int y = 0; y < targetHeight; y++) {
                System.arraycopy(source[y], 0, copy[y], 0, targetWidth);
            }
            return copy;
        }

        int sourceHeight = source.length;
        int sourceWidth = source[0].length;
        float[][] resized = new float[targetHeight][targetWidth];
        for (int y = 0; y < targetHeight; y++) {
            double sourceY = sourceCoordinate(y, sourceHeight, targetHeight);
            int y0 = (int) Math.floor(sourceY);
            int y1 = Math.min(y0 + 1, sourceHeight - 1);
            double yWeight = sourceY - y0;
            for (int x = 0; x < targetWidth; x++) {
                double sourceX = sourceCoordinate(x, sourceWidth, targetWidth);
                int x0 = (int) Math.floor(sourceX);
                int x1 = Math.min(x0 + 1, sourceWidth - 1);
                double xWeight = sourceX - x0;
                double top = source[y0][x0] + (source[y0][x1] - source[y0][x0]) * xWeight;
                double bottom = source[y1][x0] + (source[y1][x1] - source[y1][x0]) * xWeight;
                resized[y][x] = (float) (top + (bottom - top) * yWeight);
            }
        }
        return resized;
    }

    static DepthEstimationResult buildResult(float[][] depthMap) {
        validateRectangularMap(depthMap);
        int height = depthMap.length;
        int width = depthMap[0].length;
        float[] flattened = new float[width * height];
        int validCount = 0;
        double sum = 0d;
        float minimum = Float.POSITIVE_INFINITY;
        float maximum = Float.NEGATIVE_INFINITY;

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                float depth = depthMap[y][x];
                if (!Float.isFinite(depth) || depth <= 0) {
                    depth = 0f;
                } else {
                    validCount++;
                    sum += depth;
                    minimum = Math.min(minimum, depth);
                    maximum = Math.max(maximum, depth);
                }
                flattened[y * width + x] = depth;
            }
        }

        if (validCount == 0) {
            throw new IllegalStateException("Depth model returned no valid positive values");
        }
        float[] valid = new float[validCount];
        int validIndex = 0;
        for (float depth : flattened) {
            if (depth > 0) {
                valid[validIndex++] = depth;
            }
        }
        Arrays.sort(valid);
        float median = percentile(valid, 0.5f);
        return new DepthEstimationResult(width, height, "m", "row-major", validCount,
                minimum, maximum, (float) (sum / validCount), median, flattened);
    }

    private static float[][] extractDepthMap(OnnxValue output) {
        if (!(output instanceof OnnxTensor tensor)) {
            throw new IllegalStateException("Depth model output is not a tensor");
        }
        TensorInfo info = tensor.getInfo();
        requireFloatTensor(info, "output", "runtime output");
        long[] shape = info.getShape();
        int height;
        int width;
        if (shape.length == 4) {
            if (shape[0] != 1 || shape[1] != 1) {
                throw new IllegalStateException("Depth model output must have shape [1,1,H,W], actual: "
                        + Arrays.toString(shape));
            }
            height = checkedDimension(shape[2]);
            width = checkedDimension(shape[3]);
        } else if (shape.length == 3) {
            if (shape[0] != 1) {
                throw new IllegalStateException("Depth model rank-3 output must have shape [1,H,W], actual: "
                        + Arrays.toString(shape));
            }
            height = checkedDimension(shape[1]);
            width = checkedDimension(shape[2]);
        } else {
            throw new IllegalStateException("Depth model output must have rank 3 or 4, actual: "
                    + Arrays.toString(shape));
        }

        FloatBuffer values = tensor.getFloatBuffer();
        if (values == null || values.remaining() != Math.multiplyExact(height, width)) {
            throw new IllegalStateException("Depth model output buffer size does not match shape: "
                    + Arrays.toString(shape));
        }
        float[][] depthMap = new float[height][width];
        for (float[] row : depthMap) {
            values.get(row);
        }
        return depthMap;
    }

    private static int checkedDimension(long dimension) {
        if (dimension <= 0 || dimension > Integer.MAX_VALUE) {
            throw new IllegalStateException("Invalid depth output dimension: " + dimension);
        }
        return (int) dimension;
    }

    private static void validateRectangularMap(float[][] map) {
        if (map == null || map.length == 0 || map[0] == null || map[0].length == 0) {
            throw new IllegalArgumentException("depth map is null or empty");
        }
        int width = map[0].length;
        for (float[] row : map) {
            if (row == null || row.length != width) {
                throw new IllegalArgumentException("depth map must be rectangular");
            }
        }
    }

    private static float[] validValues(float[] depths, boolean inverse) {
        float[] values = new float[depths.length];
        int count = 0;
        for (float depth : depths) {
            if (Float.isFinite(depth) && depth > 0) {
                values[count++] = inverse ? 1f / depth : depth;
            }
        }
        if (count == 0) {
            throw new IllegalStateException("Depth map contains no valid positive values");
        }
        float[] result = Arrays.copyOf(values, count);
        Arrays.sort(result);
        return result;
    }

    private static float percentile(float[] sortedValues, float quantile) {
        if (sortedValues.length == 0) {
            throw new IllegalArgumentException("values must not be empty");
        }
        double position = (sortedValues.length - 1d) * quantile;
        int lowerIndex = (int) Math.floor(position);
        int upperIndex = (int) Math.ceil(position);
        if (lowerIndex == upperIndex) {
            return sortedValues[lowerIndex];
        }
        double weight = position - lowerIndex;
        return (float) (sortedValues[lowerIndex]
                + (sortedValues[upperIndex] - sortedValues[lowerIndex]) * weight);
    }

    private static int jetColor(float normalized) {
        int red = Math.round(255f * clamp(1.5f - Math.abs(4f * normalized - 3f), 0f, 1f));
        int green = Math.round(255f * clamp(1.5f - Math.abs(4f * normalized - 2f), 0f, 1f));
        int blue = Math.round(255f * clamp(1.5f - Math.abs(4f * normalized - 1f), 0f, 1f));
        return red << 16 | green << 8 | blue;
    }

    private static int blend(int sourceRgb, int heatRgb, float sourceWeight, float heatWeight) {
        int red = Math.round(((sourceRgb >> 16) & 0xff) * sourceWeight
                + ((heatRgb >> 16) & 0xff) * heatWeight);
        int green = Math.round(((sourceRgb >> 8) & 0xff) * sourceWeight
                + ((heatRgb >> 8) & 0xff) * heatWeight);
        int blue = Math.round((sourceRgb & 0xff) * sourceWeight + (heatRgb & 0xff) * heatWeight);
        return clamp(red, 0, 255) << 16 | clamp(green, 0, 255) << 8 | clamp(blue, 0, 255);
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    record LetterboxInput(FloatBuffer chw,
                          int originalWidth,
                          int originalHeight,
                          int inputWidth,
                          int inputHeight,
                          int left,
                          int top,
                          int right,
                          int bottom) {
    }

    private record Model(OrtSession session,
                         String inputName,
                         String outputName,
                         int inputWidth,
                         int inputHeight) {
    }
}
