package com.yuqiangdede.rfdetr.runtime;

import ai.onnxruntime.NodeInfo;
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OnnxValue;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import ai.onnxruntime.TensorInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuqiangdede.common.dto.output.Box;
import com.yuqiangdede.platform.common.resource.ResourcePathResolver;
import com.yuqiangdede.platform.common.runtime.ModelDescriptor;
import com.yuqiangdede.platform.common.runtime.ModelRegistry;
import com.yuqiangdede.platform.common.runtime.OnnxSessionFactory;
import com.yuqiangdede.rfdetr.config.RfDetrProperties;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class RfDetrInferenceEngine {

    static final long[] INPUT_SHAPE = {1, 3, 512, 512};
    static final long[] DETS_SHAPE = {1, 300, 4};
    static final long[] LABELS_SHAPE = {1, 300, 91};
    private static final int TOP_K = 300;

    private final ResourcePathResolver resourcePathResolver;
    private final OnnxSessionFactory onnxSessionFactory;
    private final ModelRegistry modelRegistry;
    private final ObjectMapper objectMapper;
    private final RfDetrProperties properties;

    private OrtSession session;
    private RfDetrModelMetadata metadata;
    private String inputName;
    private String detsName;
    private String labelsName;

    @PostConstruct
    void initialize() {
        Path modelPath = resourcePathResolver.resolve(properties.getModelPath());
        Path metadataPath = resourcePathResolver.resolve(properties.getMetadataPath());
        modelRegistry.register(new ModelDescriptor("rfdetr-small", modelPath.toString(), true));
        modelRegistry.validateRequiredModels();
        metadata = loadMetadata(metadataPath);
        validateMetadata(metadata);
        validateModelHash(modelPath, metadata.getModelSha256());
        try {
            session = onnxSessionFactory.createSession(modelPath.toString(), "cpu", properties.getThreads());
            validateSessionContract();
            log.info("RF-DETR Small ONNX session ready: model={}, input={}, outputs=[{}, {}]",
                    modelPath, inputName, detsName, labelsName);
        } catch (OrtException ex) {
            closeSession();
            throw new IllegalStateException("failed to create RF-DETR ONNX session: " + modelPath, ex);
        }
    }

    public List<Box> detect(BufferedImage image, Float requestedThreshold) {
        if (session == null || metadata == null) {
            throw new IllegalStateException("RF-DETR inference engine is not initialized");
        }
        float threshold = requestedThreshold == null ? properties.getThreshold() : requestedThreshold;
        float[] input = RfDetrImagePreprocessor.preprocess(image, 512, 512);
        try (OnnxTensor tensor = OnnxTensor.createTensor(onnxSessionFactory.getEnvironment(), FloatBuffer.wrap(input), INPUT_SHAPE);
             OrtSession.Result result = session.run(Map.of(inputName, tensor))) {
            float[][][] dets = tensorValue(result, detsName);
            float[][][] labels = tensorValue(result, labelsName);
            return decode(dets[0], labels[0], image.getWidth(), image.getHeight(), threshold, metadata.getClassNames());
        } catch (OrtException ex) {
            throw new IllegalStateException("RF-DETR inference failed", ex);
        }
    }

    static List<Box> decode(float[][] dets, float[][] labels, int imageWidth, int imageHeight,
                            float threshold, Map<Integer, String> classNames) {
        if (dets == null || labels == null || dets.length != labels.length) {
            throw new IllegalArgumentException("RF-DETR dets and labels query dimensions differ");
        }
        if (imageWidth <= 0 || imageHeight <= 0) {
            throw new IllegalArgumentException("image dimensions must be positive");
        }
        if (classNames == null || classNames.isEmpty()) {
            throw new IllegalArgumentException("RF-DETR class mapping is empty");
        }

        List<Candidate> candidates = new ArrayList<>();
        for (int query = 0; query < dets.length; query++) {
            if (dets[query] == null || dets[query].length != 4 || labels[query] == null) {
                throw new IllegalArgumentException("invalid RF-DETR output tensor row at query " + query);
            }
            for (Map.Entry<Integer, String> classEntry : classNames.entrySet()) {
                int classId = classEntry.getKey();
                if (classId < 0 || classId >= labels[query].length) {
                    throw new IllegalArgumentException("class id outside logits dimension: " + classId);
                }
                candidates.add(new Candidate(query, classId, sigmoid(labels[query][classId])));
            }
        }
        candidates.sort(Comparator.comparing(Candidate::score).reversed()
                .thenComparingInt(Candidate::queryIndex)
                .thenComparingInt(Candidate::classId));

        List<Box> boxes = new ArrayList<>();
        int limit = Math.min(TOP_K, candidates.size());
        for (int index = 0; index < limit; index++) {
            Candidate candidate = candidates.get(index);
            if (candidate.score() <= threshold) {
                continue;
            }
            float[] cxcywh = dets[candidate.queryIndex()];
            float x1 = clamp((cxcywh[0] - cxcywh[2] / 2.0f) * imageWidth, 0.0f, imageWidth);
            float y1 = clamp((cxcywh[1] - cxcywh[3] / 2.0f) * imageHeight, 0.0f, imageHeight);
            float x2 = clamp((cxcywh[0] + cxcywh[2] / 2.0f) * imageWidth, 0.0f, imageWidth);
            float y2 = clamp((cxcywh[1] + cxcywh[3] / 2.0f) * imageHeight, 0.0f, imageHeight);
            boxes.add(new Box(x1, y1, x2, y2, candidate.score(), (float) candidate.classId(), classNames));
        }
        return boxes;
    }

    private float[][][] tensorValue(OrtSession.Result result, String name) throws OrtException {
        OnnxValue value = result.get(name)
                .orElseThrow(() -> new IllegalStateException("RF-DETR output is missing: " + name));
        Object raw = value.getValue();
        if (!(raw instanceof float[][][] tensor)) {
            throw new IllegalStateException("RF-DETR output must be float[batch][query][feature]: " + name);
        }
        return tensor;
    }

    private RfDetrModelMetadata loadMetadata(Path metadataPath) {
        if (!Files.isRegularFile(metadataPath)) {
            throw new IllegalStateException("RF-DETR metadata is missing: " + metadataPath);
        }
        try {
            return objectMapper.readValue(metadataPath.toFile(), RfDetrModelMetadata.class);
        } catch (IOException ex) {
            throw new IllegalStateException("failed to parse RF-DETR metadata: " + metadataPath, ex);
        }
    }

    private void validateMetadata(RfDetrModelMetadata modelMetadata) {
        if (modelMetadata.getModelSha256() == null || modelMetadata.getModelSha256().length() != 64) {
            throw new IllegalStateException("RF-DETR metadata must contain a modelSha256");
        }
        validateTensorSpec(modelMetadata.getInput(), "input", INPUT_SHAPE);
        validateTensorSpec(modelMetadata.getOutputs().get("dets"), "dets", DETS_SHAPE);
        validateTensorSpec(modelMetadata.getOutputs().get("labels"), "labels", LABELS_SHAPE);
        if (!CocoClassNames.standard().equals(modelMetadata.getClassNames())) {
            throw new IllegalStateException("RF-DETR metadata must use the canonical sparse COCO class mapping");
        }
    }

    private void validateTensorSpec(RfDetrModelMetadata.TensorSpec spec, String expectedName, long[] expectedShape) {
        if (spec == null || !expectedName.equals(spec.getName()) || !"float32".equalsIgnoreCase(spec.getType())
                || spec.getShape() == null || !Arrays.equals(toLongArray(spec.getShape()), expectedShape)) {
            throw new IllegalStateException("RF-DETR metadata tensor contract mismatch: " + expectedName);
        }
    }

    private void validateModelHash(Path modelPath, String expectedSha256) {
        try (InputStream inputStream = Files.newInputStream(modelPath)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[1024 * 1024];
            for (int read; (read = inputStream.read(buffer)) >= 0;) {
                digest.update(buffer, 0, read);
            }
            String actual = HexFormat.of().formatHex(digest.digest());
            if (!actual.equalsIgnoreCase(expectedSha256)) {
                throw new IllegalStateException("RF-DETR model SHA-256 mismatch: expected=" + expectedSha256 + ", actual=" + actual);
            }
        } catch (IOException ex) {
            throw new IllegalStateException("failed to read RF-DETR model: " + modelPath, ex);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private void validateSessionContract() throws OrtException {
        inputName = validateNode(session.getInputInfo(), metadata.getInput(), INPUT_SHAPE);
        detsName = validateNode(session.getOutputInfo(), metadata.getOutputs().get("dets"), DETS_SHAPE);
        labelsName = validateNode(session.getOutputInfo(), metadata.getOutputs().get("labels"), LABELS_SHAPE);
    }

    private String validateNode(Map<String, NodeInfo> infoMap, RfDetrModelMetadata.TensorSpec spec,
                                long[] expectedShape) {
        NodeInfo nodeInfo = infoMap.get(spec.getName());
        if (nodeInfo == null || !(nodeInfo.getInfo() instanceof TensorInfo tensorInfo)
                || !"FLOAT".equals(String.valueOf(tensorInfo.type))
                || !Arrays.equals(tensorInfo.getShape(), expectedShape)) {
            throw new IllegalStateException("RF-DETR ONNX tensor contract mismatch: " + spec.getName());
        }
        return spec.getName();
    }

    private static long[] toLongArray(List<Long> values) {
        return values.stream().mapToLong(Long::longValue).toArray();
    }

    private static float sigmoid(float value) {
        float clipped = Math.max(-88.0f, Math.min(88.0f, value));
        return (float) (1.0d / (1.0d + Math.exp(-clipped)));
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    @PreDestroy
    void destroy() {
        closeSession();
    }

    private void closeSession() {
        if (session != null) {
            try {
                session.close();
            } catch (OrtException ex) {
                log.warn("failed to close RF-DETR ONNX session", ex);
            } finally {
                session = null;
            }
        }
    }

    private record Candidate(int queryIndex, int classId, float score) {
    }
}
