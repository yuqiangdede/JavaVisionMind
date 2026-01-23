package com.yuqiangdede.yolo.util.yolo;

import ai.djl.huggingface.tokenizers.Encoding;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import ai.onnxruntime.TensorInfo;
import com.yuqiangdede.yolo.config.Constant;
import com.yuqiangdede.yolo.dto.output.YoloDetectionResult;
import lombok.extern.slf4j.Slf4j;
import org.opencv.core.Mat;

import java.io.IOException;
import java.nio.FloatBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class YoloV26TextPromptUtil {

    private static final int MAX_PROMPTS = 80;
    private static final int MAX_TOKENS = 77;
    private static final int EOS_TOKEN = 49407;
    private static final int PAD_TOKEN = 0;
    private static final String[] PROMPT_INIT_NAMES = new String[]{
            "/model.23/one2one_cv4.0/Div_output_0",
            "/model.23/one2one_cv4.1/Div_output_0",
            "/model.23/one2one_cv4.2/Div_output_0"
    };

    private static final OrtEnvironment ENV = OrtEnvironment.getEnvironment();
    private static volatile OrtSession textSession;
    private static volatile String textInputName;
    private static volatile HuggingFaceTokenizer tokenizer;
    private static volatile ModelSpec modelSpec;

    public static YoloDetectionResult predictor(Mat mat, Float conf, List<String> prompts) {
        if (mat == null || mat.empty()) {
            throw new IllegalArgumentException("mat is null or empty");
        }
        if (prompts == null || prompts.isEmpty()) {
            throw new IllegalArgumentException("classes is null or empty");
        }
        int promptCount = Math.min(prompts.size(), MAX_PROMPTS);
        List<String> usedPrompts = prompts.subList(0, promptCount);

        float[][] embeddings = embedPrompts(usedPrompts);
        float[] promptTable = buildPromptTable(embeddings);
        Map<Integer, String> classNames = buildClassNames(usedPrompts);

        ModelSpec spec = getModelSpec();
        float threshold = conf == null ? Constant.CONF_THRESHOLD : conf;

        try (PromptSession promptSession = createPromptSession(promptTable)) {
            try (OnnxTensor input = YoloBaseUtil.transferTensor(mat, spec.model)) {
                try (OrtSession.Result result = promptSession.session.run(Collections.singletonMap("images", input))) {
                    float[][] data = ((float[][][]) result.get(0).getValue())[0];
                    List<List<Float>> boxes = decodeBoxes(data, mat, spec, threshold);
                    List<List<Float>> boxesAfterNms = boxes.stream()
                            .map(List::copyOf)
                            .toList();
                    if (spec.model.nmsEnabled) {
                        boxesAfterNms = NMS(spec.model, boxes).stream()
                                .map(List::copyOf)
                                .toList();
                    }
                    return new YoloDetectionResult(boxesAfterNms, classNames);
                }
            }
        } catch (OrtException e) {
            log.error("text prompt detect error", e);
            throw new RuntimeException(e);
        }
    }

    private static float[][] embedPrompts(List<String> prompts) {
        HuggingFaceTokenizer tokenizer = getTokenizer();
        OrtSession session = getTextSession();
        String inputName = getTextInputName(session);

        float[][] embeddings = new float[prompts.size()][];
        for (int i = 0; i < prompts.size(); i++) {
            int[] tokens = encodePrompt(tokenizer, prompts.get(i));
            embeddings[i] = embedText(session, inputName, tokens);
        }
        return embeddings;
    }

    private static int[] encodePrompt(HuggingFaceTokenizer tokenizer, String prompt) {
        Encoding encoding = tokenizer.encode(prompt == null ? "" : prompt);
        long[] ids = encoding.getIds();
        int[] tokens = new int[MAX_TOKENS];
        int length = Math.min(ids.length, MAX_TOKENS);
        for (int i = 0; i < length; i++) {
            tokens[i] = (int) ids[i];
        }
        if (ids.length > MAX_TOKENS) {
            tokens[MAX_TOKENS - 1] = EOS_TOKEN;
        } else if (length < MAX_TOKENS) {
            for (int i = length; i < MAX_TOKENS; i++) {
                tokens[i] = PAD_TOKEN;
            }
        }
        return tokens;
    }

    private static float[] embedText(OrtSession session, String inputName, int[] tokens) {
        long[] input = toLongArray(tokens);
        try (OnnxTensor inputTensor = OnnxTensor.createTensor(ENV, new long[][]{input})) {
            try (OrtSession.Result result = session.run(Collections.singletonMap(inputName, inputTensor))) {
                float[][] output = (float[][]) result.get(0).getValue();
                float[] vector = output[0];
                float scale = Constant.YOLO_TEXT_PROMPT_SCALE;
                if (Math.abs(scale - 1.0f) > 1e-6f) {
                    for (int i = 0; i < vector.length; i++) {
                        vector[i] *= scale;
                    }
                }
                return vector;
            }
        } catch (OrtException e) {
            throw new RuntimeException("Text encoder inference failed", e);
        }
    }

    private static float[] buildPromptTable(float[][] embeddings) {
        int embedDim = embeddings.length == 0 ? 512 : embeddings[0].length;
        float[] table = new float[MAX_PROMPTS * embedDim];
        for (int i = 0; i < embeddings.length && i < MAX_PROMPTS; i++) {
            float[] vec = embeddings[i];
            if (vec == null || vec.length != embedDim) {
                continue;
            }
            System.arraycopy(vec, 0, table, i * embedDim, embedDim);
        }
        return table;
    }

    private static Map<Integer, String> buildClassNames(List<String> prompts) {
        Map<Integer, String> classNames = new HashMap<>();
        for (int i = 0; i < prompts.size(); i++) {
            classNames.put(i, prompts.get(i));
        }
        return classNames;
    }

    private static List<List<Float>> decodeBoxes(float[][] data, Mat mat, ModelSpec spec, float threshold) {
        float scaleW = (float) Math.max(mat.width(), mat.height()) / spec.model.netWidth;
        float scaleH = (float) Math.max(mat.width(), mat.height()) / spec.model.netHeight;

        List<List<Float>> boxes = new ArrayList<>();
        for (float[] row : data) {
            float score = row[4];
            if (score <= threshold) {
                continue;
            }
            float x1 = row[0] * scaleW;
            float y1 = row[1] * scaleH;
            float x2 = row[2] * scaleW;
            float y2 = row[3] * scaleH;
            float classId = row[5];
            boxes.add(new ArrayList<>(Arrays.asList(x1, y1, x2, y2, score, classId)));
        }
        return boxes;
    }

    private static HuggingFaceTokenizer getTokenizer() {
        if (tokenizer != null) {
            return tokenizer;
        }
        synchronized (YoloV26TextPromptUtil.class) {
            if (tokenizer != null) {
                return tokenizer;
            }
            Path path = Paths.get(Constant.YOLO_TEXT_TOKENIZER_PATH);
            if (!Files.exists(path)) {
                throw new IllegalStateException("Tokenizer path not found: " + path);
            }
            try {
                tokenizer = HuggingFaceTokenizer.newInstance(path);
                return tokenizer;
            } catch (IOException e) {
                throw new RuntimeException("Failed to load tokenizer", e);
            }
        }
    }

    private static OrtSession getTextSession() {
        if (textSession != null) {
            return textSession;
        }
        synchronized (YoloV26TextPromptUtil.class) {
            if (textSession != null) {
                return textSession;
            }
            Path path = Paths.get(Constant.YOLO_TEXT_ENCODER_ONNX_PATH);
            if (!Files.exists(path)) {
                throw new IllegalStateException("Text encoder model not found: " + path);
            }
            try {
                textSession = ENV.createSession(path.toString(), new OrtSession.SessionOptions());
                return textSession;
            } catch (OrtException e) {
                throw new RuntimeException("Failed to load text encoder", e);
            }
        }
    }

    private static String getTextInputName(OrtSession session) {
        if (textInputName != null) {
            return textInputName;
        }
        synchronized (YoloV26TextPromptUtil.class) {
            if (textInputName == null) {
                textInputName = session.getInputNames().iterator().next();
            }
            return textInputName;
        }
    }

    private static ModelSpec getModelSpec() {
        if (modelSpec != null) {
            return modelSpec;
        }
        synchronized (YoloV26TextPromptUtil.class) {
            if (modelSpec != null) {
                return modelSpec;
            }
            try (OrtSession session = ENV.createSession(Constant.YOLO_TEXT_ONNX_PATH, new OrtSession.SessionOptions())) {
                TensorInfo info = (TensorInfo) session.getInputInfo().get("images").getInfo();
                long netHeight = info.getShape()[2];
                long netWidth = info.getShape()[3];
                if (netHeight <= 0 || netWidth <= 0) {
                    netHeight = 640;
                    netWidth = 640;
                }
                YoloBaseUtil.Model model = new YoloBaseUtil.Model(
                        ENV,
                        null,
                        Collections.emptyMap(),
                        1,
                        3,
                        netHeight,
                        netWidth,
                        Constant.CONF_THRESHOLD,
                        Constant.NMS_THRESHOLD,
                        Constant.YOLO_TEXT_NMS_ENABLED
                );
                modelSpec = new ModelSpec(model);
                return modelSpec;
            } catch (OrtException e) {
                throw new RuntimeException("Failed to read text model spec", e);
            }
        }
    }

    private static PromptSession createPromptSession(float[] promptTable) throws OrtException {
        int embedDim = promptTable.length / MAX_PROMPTS;
        OrtSession.SessionOptions options = new OrtSession.SessionOptions();
        OnnxTensor[] initTensors = new OnnxTensor[PROMPT_INIT_NAMES.length];
        boolean success = false;
        try {
            for (int i = 0; i < PROMPT_INIT_NAMES.length; i++) {
                initTensors[i] = OnnxTensor.createTensor(ENV, FloatBuffer.wrap(promptTable),
                        new long[]{1, MAX_PROMPTS, embedDim});
                options.addInitializer(PROMPT_INIT_NAMES[i], initTensors[i]);
            }
            OrtSession session = ENV.createSession(Constant.YOLO_TEXT_ONNX_PATH, options);
            PromptSession promptSession = new PromptSession(session, options, initTensors);
            success = true;
            return promptSession;
        } finally {
            if (!success) {
                for (OnnxTensor tensor : initTensors) {
                    if (tensor != null) {
                        tensor.close();
                    }
                }
                options.close();
            }
        }
    }

    private static long[] toLongArray(int[] arr) {
        long[] result = new long[arr.length];
        for (int i = 0; i < arr.length; i++) {
            result[i] = arr[i];
        }
        return result;
    }

    private static List<ArrayList<Float>> NMS(YoloBaseUtil.Model model, List<List<Float>> boxes) {
        int[] indexs = new int[boxes.size()];
        Arrays.fill(indexs, 1);

        for (int cur = 0; cur < boxes.size(); cur++) {
            if (indexs[cur] == 0) {
                continue;
            }
            ArrayList<Float> curMaxConf = new ArrayList<>(boxes.get(cur));

            for (int i = cur + 1; i < boxes.size(); i++) {
                if (indexs[i] == 0) {
                    continue;
                }
                float classIndex = boxes.get(i).get(5);
                if (classIndex == curMaxConf.get(5)) {
                    float x1 = curMaxConf.get(0);
                    float y1 = curMaxConf.get(1);
                    float x2 = curMaxConf.get(2);
                    float y2 = curMaxConf.get(3);
                    float x3 = boxes.get(i).get(0);
                    float y3 = boxes.get(i).get(1);
                    float x4 = boxes.get(i).get(2);
                    float y4 = boxes.get(i).get(3);

                    if (x1 > x4 || x2 < x3 || y1 > y4 || y2 < y3) {
                        continue;
                    }

                    float intersectionWidth = Math.max(x1, x3) - Math.min(x2, x4);
                    float intersectionHeight = Math.max(y1, y3) - Math.min(y2, y4);
                    float intersectionArea = Math.max(0, intersectionWidth * intersectionHeight);
                    float unionArea = (x2 - x1) * (y2 - y1) + (x4 - x3) * (y4 - y3) - intersectionArea;
                    float iou = intersectionArea / unionArea;

                    if (iou > model.nmsThreshold) {
                        if (boxes.get(i).get(4) <= curMaxConf.get(4)) {
                            indexs[i] = 0;
                        } else {
                            indexs[cur] = 0;
                        }
                    }
                }
            }
        }

        List<ArrayList<Float>> resBoxes = new ArrayList<>();
        for (int index = 0; index < indexs.length; index++) {
            if (indexs[index] == 1) {
                resBoxes.add(new ArrayList<>(boxes.get(index)));
            }
        }

        return resBoxes;
    }

    private static final class PromptSession implements AutoCloseable {
        private final OrtSession session;
        private final OrtSession.SessionOptions options;
        private final OnnxTensor[] initTensors;

        private PromptSession(OrtSession session, OrtSession.SessionOptions options, OnnxTensor[] initTensors) {
            this.session = session;
            this.options = options;
            this.initTensors = initTensors;
        }

        @Override
        public void close() {
            try {
                session.close();
            } catch (OrtException e) {
                log.warn("Failed to close prompt session", e);
            }
            for (OnnxTensor tensor : initTensors) {
                if (tensor != null) {
                    tensor.close();
                }
            }
            options.close();
        }
    }

    private static final class ModelSpec {
        private final YoloBaseUtil.Model model;

        private ModelSpec(YoloBaseUtil.Model model) {
            this.model = model;
        }
    }
}
