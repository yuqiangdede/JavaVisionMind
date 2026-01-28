package com.yuqiangdede.lpr.service;

import ai.onnxruntime.NodeInfo;
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import ai.onnxruntime.TensorInfo;
import com.yuqiangdede.lpr.config.LprProperties;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.IOException;
import java.nio.FloatBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Component
@Slf4j
public class LprOnnxRecognizer {

    private final LprProperties properties;
    private final List<String> alphabet;
    private final int blankIndex;
    private final int provinceCount;

    private OrtEnvironment environment;
    private OrtSession session;
    private String inputName;
    private int outputSequenceLength = -1;

    public LprOnnxRecognizer(LprProperties properties) {
        this.properties = properties;
        List<String> provinces = defaultProvinceSymbols();
        this.alphabet = defaultAlphabet(provinces);
        this.provinceCount = provinces.size();
        this.blankIndex = this.alphabet.size() - 1;
    }

    /**
     * 初始化 ONNX 环境与会话，并加载车牌识别模型。
     */
    @PostConstruct
    public void initialize() throws IOException {
        Path modelPath = resolveModelPath(properties.getModelPath());
        if (!Files.exists(modelPath)) {
            throw new IOException("License plate recognition model not found: " + modelPath);
        }
        try {
            this.environment = OrtEnvironment.getEnvironment();
            try (OrtSession.SessionOptions options = new OrtSession.SessionOptions()) {
                this.session = environment.createSession(modelPath.toString(), options);
            }
            this.inputName = session.getInputNames().iterator().next();
            detectOutputLayout();
            log.info("LPR 模型加载成功: {}", modelPath);
        } catch (OrtException e) {
            throw new IllegalStateException("Failed to load LPR model: " + modelPath, e);
        }
    }


    /**
     * 使用 ONNX 模型识别输入图像中的车牌文字。
     *
     * @param image 输入图像（不可为 null）
     * @return 识别结果；失败时返回空字符串
     */
    public String recognize(BufferedImage image) {
        Objects.requireNonNull(image, "image must not be null");
        float[] chw = preprocess(image);
        long[] shape = new long[]{1, 3, properties.getModelInputHeight(), properties.getModelInputWidth()};
        try (OnnxTensor tensor = OnnxTensor.createTensor(environment, FloatBuffer.wrap(chw), shape);
             OrtSession.Result result = session.run(Collections.singletonMap(inputName, tensor))) {
            float[][] logits = extractLogits(result);
            return decode(logits);
        } catch (OrtException e) {
            log.error("LPR 推理失败", e);
            return "";
        }
    }

    /**
     * 根据输出张量形状推断模型输出布局（序列长度位置）。
     */
    private void detectOutputLayout() throws OrtException {
        Map<String, NodeInfo> outputInfo = session.getOutputInfo();
        if (outputInfo.isEmpty()) {
            throw new IllegalStateException("LPR model output node is not defined");
        }
        NodeInfo nodeInfo = outputInfo.values().iterator().next();
        if (!(nodeInfo.getInfo() instanceof TensorInfo tensorInfo)) {
            throw new IllegalStateException("LPR model output is not a tensor");
        }
        long[] shape = tensorInfo.getShape();
        if (shape.length != 3) {
            log.warn("未知的输出形状: {}", shape);
            return;
        }
        long dim1 = shape[1];
        long dim2 = shape[2];
        if (dim1 == alphabet.size()) {
            outputSequenceLength = (int) dim2;
        } else if (dim2 == alphabet.size()) {
            outputSequenceLength = (int) dim1;
        } else {
            log.warn("模型输出维度 {}x{} 与字典大小 {} 不匹配", dim1, dim2, alphabet.size());
        }
    }

    /**
     * 将输入图像缩放并转换为 CHW 浮点数组（灰度化 + 轻微对比度拉伸，再归一化到近似 [-1, 1]）。
     */
    private float[] preprocess(BufferedImage source) {
        BufferedImage canvas = new BufferedImage(
                properties.getModelInputWidth(),
                properties.getModelInputHeight(),
                BufferedImage.TYPE_3BYTE_BGR
        );
        Graphics2D graphics = canvas.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        graphics.drawImage(source, 0, 0, canvas.getWidth(), canvas.getHeight(), null);
        graphics.dispose();

        byte[] data = ((DataBufferByte) canvas.getRaster().getDataBuffer()).getData();
        int planeSize = canvas.getWidth() * canvas.getHeight();
        float[] chw = new float[planeSize * 3];
        float contrast = 1.10f;
        for (int i = 0; i < planeSize; i++) {
            int base = i * 3;
            int b = data[base] & 0xFF;
            int g = data[base + 1] & 0xFF;
            int r = data[base + 2] & 0xFF;
            int gray = (r * 77 + g * 150 + b * 29) >> 8;
            float enhanced = (gray - 127.5f) * contrast + 127.5f;
            if (enhanced < 0f) {
                enhanced = 0f;
            } else if (enhanced > 255f) {
                enhanced = 255f;
            }
            float normalized = (enhanced - 127.5f) * 0.0078125f;
            chw[i] = normalized;
            chw[i + planeSize] = normalized;
            chw[i + planeSize * 2] = normalized;
        }
        return chw;
    }

    /**
     * 从 ONNX 输出中提取 logits，并按 [seq, classes] 排列。
     */
    private float[][] extractLogits(OrtSession.Result result) throws OrtException {
        OnnxTensor tensor = (OnnxTensor) result.get(0);
        long[] shape = tensor.getInfo().getShape();
        if (shape.length != 3) {
            throw new IllegalStateException("Unexpected LPR output shape: " + java.util.Arrays.toString(shape));
        }
        float[][][] raw = (float[][][]) tensor.getValue();
        int dim1 = (int) shape[1];
        int dim2 = (int) shape[2];

        if (dim1 == alphabet.size()) {
            return transpose(raw[0], dim1, dim2);
        } else if (dim2 == alphabet.size()) {
            return raw[0];
        } else {
            throw new IllegalStateException(
                    String.format(Locale.ROOT, "Output shape (%d,%d) does not match alphabet size %d", dim1, dim2,
                            alphabet.size())
            );
        }
    }

    /**
     * 转置 logits 维度，得到 [seq, classes]。
     */
    private float[][] transpose(float[][] logits, int classes, int seq) {
        float[][] transposed = new float[seq][classes];
        for (int c = 0; c < classes; c++) {
            for (int t = 0; t < seq; t++) {
                transposed[t][c] = logits[c][t];
            }
        }
        return transposed;
    }

    /**
     * CTC 解码：首位限定为省简称，去除 blank 并合并连续重复。
     */
    private String decode(float[][] logits) {
        StringBuilder builder = new StringBuilder();
        int prevClass = -1;
        boolean firstEmitted = false;
        int steps = outputSequenceLength > 0 ? Math.min(outputSequenceLength, logits.length) : logits.length;
        for (int t = 0; t < steps; t++) {
            float[] step = logits[t];
            int current = firstEmitted ? argMax(step) : argMaxProvinceOrBlank(step);
            if (current == blankIndex) {
                prevClass = current;
                continue;
            }
            if (current != prevClass && current < alphabet.size()) {
                if (!firstEmitted && current >= provinceCount) {
                    prevClass = current;
                    continue;
                }
                builder.append(alphabet.get(current));
                firstEmitted = true;
            }
            prevClass = current;
        }
        return builder.toString();
    }

    /**
     * 首位限制：仅允许省简称或 blank。
     */
    private int argMaxProvinceOrBlank(float[] values) {
        int bestIndex = blankIndex;
        float best = values[blankIndex];
        int limit = Math.min(provinceCount, values.length);
        for (int i = 0; i < limit; i++) {
            if (values[i] > best) {
                best = values[i];
                bestIndex = i;
            }
        }
        return bestIndex;
    }

    /**
     * 返回数组中最大值的下标。
     */
    private int argMax(float[] values) {
        int index = 0;
        float max = values[0];
        for (int i = 1; i < values.length; i++) {
            if (values[i] > max) {
                max = values[i];
                index = i;
            }
        }
        return index;
    }

    /**
     * 解析模型路径：支持相对路径并结合 VISION_MIND_PATH。
     */
    private Path resolveModelPath(String configured) {
        Path candidate = Paths.get(configured);
        if (!candidate.isAbsolute()) {
            String base = System.getenv("VISION_MIND_PATH");
            if (base != null && !base.isBlank()) {
                candidate = Paths.get(base).resolve(configured.replaceFirst("^[/\\\\]+", ""));
            }
        }
        return candidate.toAbsolutePath().normalize();
    }

    /**
     * 构建默认字典（省份简称 + 数字 + 字母 + blank）。
     */
    private List<String> defaultAlphabet(List<String> provinces) {
        List<String> symbols = new ArrayList<>(provinces);
        for (char c = '0'; c <= '9'; c++) {
            symbols.add(String.valueOf(c));
        }
        Collections.addAll(symbols,
                "A", "B", "C", "D", "E", "F", "G", "H",
                "J", "K", "L", "M", "N",
                "P", "Q", "R", "S", "T", "U", "V", "W", "X", "Y", "Z",
                "I", "O"
        );
        symbols.add("-"); // CTCLoss blank, matches训练脚本
        return List.copyOf(symbols);
    }

    /**
     * 构建省份简称字典。
     */
    private List<String> defaultProvinceSymbols() {
        List<String> symbols = new ArrayList<>();
        Collections.addAll(symbols,
                "京", "沪", "津", "渝", "冀", "晋", "蒙", "辽", "吉", "黑",
                "苏", "浙", "皖", "闽", "赣", "鲁", "豫", "鄂", "湘", "粤",
                "桂", "琼", "川", "贵", "云", "藏", "陕", "甘", "青", "宁",
                "新"
        );
        return List.copyOf(symbols);
    }

    /**
     * 关闭 ONNX 会话与环境资源。
     */
    @PreDestroy
    public void destroy() {
        if (session != null) {
            try {
                session.close();
            } catch (OrtException e) {
                log.warn("关闭 LPR Session 失败: {}", e.getMessage());
            }
        }
        if (environment != null) {
            try {
                environment.close();
            } catch (Exception e) {
                log.warn("关闭 ONNX Environment 失败: {}", e.getMessage());
            }
        }
    }
}
