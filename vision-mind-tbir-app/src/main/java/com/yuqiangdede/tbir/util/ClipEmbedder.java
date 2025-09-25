package com.yuqiangdede.tbir.util;

import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.onnxruntime.*;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.FloatBuffer;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.yuqiangdede.tbir.config.Constant.CLIP_TOKENIZER;

public class ClipEmbedder {

    private final OrtEnvironment env;
    private final OrtSession imageSession;
    private final OrtSession textSession;

    public ClipEmbedder(String imageModelPath, String textModelPath) throws OrtException {
        this.env = OrtEnvironment.getEnvironment();
        this.imageSession = env.createSession(imageModelPath, new OrtSession.SessionOptions());
        System.out.println("image model inputs: " + imageSession.getInputNames());

        this.textSession = env.createSession(textModelPath, new OrtSession.SessionOptions());
        System.out.println("text model inputs: " + textSession.getInputNames());

    }

    /**
     * 将图像嵌入为浮点数数组
     *
     * @param image 输入的图像，类型为BufferedImage
     * @return 嵌入后的浮点数数组
     * @throws OrtException 如果处理图像或执行ONNX推理时发生异常
     */
    public float[] embedImage(BufferedImage image) throws OrtException {
        BufferedImage resized = ImageCropAndAugmentUtil.resizeWithAspectAndPad(image, 224);
        float[] chw = normalizeAndConvert(resized);

        OnnxTensor input = OnnxTensor.createTensor(env, FloatBuffer.wrap(chw), new long[]{1, 3, 224, 224});
        OrtSession.Result result = imageSession.run(Collections.singletonMap("pixel_values", input));
        float[][] output = (float[][]) result.get(0).getValue();
        return output[0];
    }

    /**
     * 文本向量接口
     *
     * @param inputIds      文本输入ID数组
     * @param attentionMask 注意力掩码数组
     * @return 文本向量数组
     * @throws OrtException 如果在执行过程中遇到Ort运行时异常，则抛出此异常
     */
    public float[] embedText(int[] inputIds, int[] attentionMask) throws OrtException {
        OnnxTensor ids = OnnxTensor.createTensor(env, new long[][]{toLongArray(inputIds)});
        OnnxTensor mask = OnnxTensor.createTensor(env, new long[][]{toLongArray(attentionMask)});
        Map<String, OnnxTensor> inputs = Map.of(
                "input_ids", ids,
                "attention_mask", mask
        );
        OrtSession.Result result = textSession.run(inputs);
        float[][] output = (float[][]) result.get(0).getValue();
        return output[0];
    }

    /**
     * 将BufferedImage图像进行归一化和转换，并返回归一化后的浮点数数组。
     *
     * @param image 要进行归一化和转换的BufferedImage图像
     * @return 归一化后的浮点数数组，长度为3 * 图像宽度 * 图像高度
     */
    private float[] normalizeAndConvert(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        float[] result = new float[3 * width * height];
        int[] rgb = new int[3];
        int idxR = 0, idxG = width * height, idxB = 2 * width * height;

        float[] mean = {0.48145466f, 0.4578275f, 0.40821073f};
        float[] std = {0.26862954f, 0.26130258f, 0.27577711f};

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int pixel = image.getRGB(x, y);
                rgb[0] = (pixel >> 16) & 0xff;
                rgb[1] = (pixel >> 8) & 0xff;
                rgb[2] = pixel & 0xff;

                int i = y * width + x;
                result[idxR + i] = (rgb[0] / 255.0f - mean[0]) / std[0];
                result[idxG + i] = (rgb[1] / 255.0f - mean[1]) / std[1];
                result[idxB + i] = (rgb[2] / 255.0f - mean[2]) / std[2];
            }
        }
        return result;
    }

    /**
     * 将整型数组转换为长整型数组
     *
     * @param arr 待转换的整型数组
     * @return 转换后的长整型数组
     */
    private long[] toLongArray(int[] arr) {
        long[] result = new long[arr.length];
        for (int i = 0; i < arr.length; i++) result[i] = arr[i];
        return result;
    }

    /**
     * 根据给定的文本提示嵌入文本
     *
     * @param prompt 要嵌入的文本提示
     * @return 嵌入后的浮点数组
     * @throws RuntimeException 如果嵌入文本失败，则抛出运行时异常
     */
    public float[] embedText(String prompt) {
        try {
            HuggingFaceTokenizer tokenizer = HuggingFaceTokenizer.newInstance(Paths.get(CLIP_TOKENIZER));

            var result = tokenizer.encode(prompt);

            long[] longInputIds = result.getIds();
            long[] longAttentionMask = result.getAttentionMask();

            // 转为 int[]
            int[] inputIds = new int[longInputIds.length];
            int[] attentionMask = new int[longAttentionMask.length];

            for (int i = 0; i < inputIds.length; i++) {
                inputIds[i] = (int) longInputIds[i];
                attentionMask[i] = (int) longAttentionMask[i];
            }

            return embedText(inputIds, attentionMask);
        } catch (IOException | OrtException e) {
            throw new RuntimeException("Text embedding failed", e);
        }
    }

    /**
     * 将文本列表嵌入到向量空间中。
     *
     * @param expandedPrompts 待嵌入的文本列表
     * @return 嵌入后的向量列表，每个文本对应一个向量
     */
    public List<float[]> embedTexts(List<String> expandedPrompts) {
        List<float[]> vectors = new ArrayList<>();
        for (String prompt : expandedPrompts) {
            if (null == prompt || prompt.isEmpty()) {
                continue;
            }
            float[] vec = embedText(prompt);
            vectors.add(vec);
        }
        return vectors;
    }
}
