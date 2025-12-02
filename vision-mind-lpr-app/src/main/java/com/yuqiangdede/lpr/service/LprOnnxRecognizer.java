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

    private OrtEnvironment environment;
    private OrtSession session;
    private String inputName;
    private int outputSequenceLength = -1;

    public LprOnnxRecognizer(LprProperties properties) {
        this.properties = properties;
        this.alphabet = defaultAlphabet();
        this.blankIndex = this.alphabet.size() - 1;
    }

    @PostConstruct
    public void initialize() throws IOException {
        Path modelPath = resolveModelPath(properties.getModelPath());
        if (!Files.exists(modelPath)) {
            throw new IOException("无法找到车牌识别模型: " + modelPath);
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
            throw new IllegalStateException("加载 LPR 模型失败: " + modelPath, e);
        }
    }

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

    private void detectOutputLayout() throws OrtException {
        Map<String, NodeInfo> outputInfo = session.getOutputInfo();
        if (outputInfo.isEmpty()) {
            throw new IllegalStateException("LPR 模型未定义输出节点");
        }
        NodeInfo nodeInfo = outputInfo.values().iterator().next();
        if (!(nodeInfo.getInfo() instanceof TensorInfo tensorInfo)) {
            throw new IllegalStateException("LPR 模型输出不是张量");
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
        for (int i = 0; i < planeSize; i++) {
            int base = i * 3;
            float b = (data[base] & 0xFF) - 127.5f;
            float g = (data[base + 1] & 0xFF) - 127.5f;
            float r = (data[base + 2] & 0xFF) - 127.5f;
            b *= 0.0078125f;
            g *= 0.0078125f;
            r *= 0.0078125f;
            chw[i] = b;
            chw[i + planeSize] = g;
            chw[i + planeSize * 2] = r;
        }
        return chw;
    }

    private float[][] extractLogits(OrtSession.Result result) throws OrtException {
        OnnxTensor tensor = (OnnxTensor) result.get(0);
        long[] shape = tensor.getInfo().getShape();
        if (shape.length != 3) {
            throw new IllegalStateException("LPR 输出维度异常: " + java.util.Arrays.toString(shape));
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
                    String.format(Locale.ROOT, "输出维度(%d,%d) 与字典大小 %d 不匹配", dim1, dim2, alphabet.size())
            );
        }
    }

    private float[][] transpose(float[][] logits, int classes, int seq) {
        float[][] transposed = new float[seq][classes];
        for (int c = 0; c < classes; c++) {
            for (int t = 0; t < seq; t++) {
                transposed[t][c] = logits[c][t];
            }
        }
        return transposed;
    }

    private String decode(float[][] logits) {
        StringBuilder builder = new StringBuilder();
        int prevClass = -1;
        int steps = outputSequenceLength > 0 ? Math.min(outputSequenceLength, logits.length) : logits.length;
        for (int t = 0; t < steps; t++) {
            float[] step = logits[t];
            int current = argMax(step);
            if (current == blankIndex) {
                prevClass = current;
                continue;
            }
            if (current != prevClass && current < alphabet.size()) {
                builder.append(alphabet.get(current));
            }
            prevClass = current;
        }
        return builder.toString();
    }

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

    private List<String> defaultAlphabet() {
        List<String> symbols = new ArrayList<>();
        Collections.addAll(symbols,
                "京", "沪", "津", "渝", "冀", "晋", "蒙", "辽", "吉", "黑",
                "苏", "浙", "皖", "闽", "赣", "鲁", "豫", "鄂", "湘", "粤",
                "桂", "琼", "川", "贵", "云", "藏", "陕", "甘", "青", "宁",
                "新"
        );
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
