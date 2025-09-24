package com.yuqiangdede.common.util;

public class VectorUtil {

    /**
     * 归一化向量。
     *
     * @param vector 输入向量
     * @return 归一化后的向量，若输入向量为零向量则返回原向量
     */
    public static float[] normalizeVector(float[] vector) {
        // 计算向量的L2范数
        double norm = 0.0;
        for (float v : vector) {
            norm += v * v;
        }
        norm = Math.sqrt(norm);

        // 若范数为0，返回原向量
        if (norm == 0) {
            return vector;
        }

        // 归一化向量
        float[] normalizedVector = new float[vector.length];
        for (int i = 0; i < vector.length; i++) {
            normalizedVector[i] = vector[i] / (float) norm;
        }
        return normalizedVector;
    }


    /**
     * 计算两个向量的余弦相似度。
     *
     * @param vectorA 第一个浮点数数组表示的向量。
     * @param vectorB 第二个浮点数数组表示的向量。
     * @return 两个向量的余弦相似度，取值范围为[-1, 1]。
     * @throws IllegalArgumentException 如果两个向量的长度不相等，或者任意一个向量的范数为0，则抛出此异常。
     */
    public static double calculateCosineSimilarity(float[] vectorA, float[] vectorB) {
        if (vectorA.length != vectorB.length) {
            throw new IllegalArgumentException("Vectors must be of the same length.");
        }

        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        for (int i = 0; i < vectorA.length; i++) {
            dotProduct += vectorA[i] * vectorB[i];
            normA += Math.pow(vectorA[i], 2);
            normB += Math.pow(vectorB[i], 2);
        }

        normA = Math.sqrt(normA);
        normB = Math.sqrt(normB);

        if (normA == 0 || normB == 0) {
            throw new IllegalArgumentException("Vector norm cannot be 0.");
        }

        return dotProduct / (normA * normB);
    }
}
