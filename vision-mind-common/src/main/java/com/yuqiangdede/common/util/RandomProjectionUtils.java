package com.yuqiangdede.common.util;

import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import static com.yuqiangdede.common.Constant.MATRIX_PATH;

public class RandomProjectionUtils {
    private static final float[][] projectionMatrix = loadMatrix();
    private static final int inputDim = 2048;
    private static final int targetDim = 1024;

    private static float[][] loadMatrix() {
        try {
            InputStream is = new FileInputStream(MATRIX_PATH);
            DataInputStream dis = new DataInputStream(is);
            int rows = dis.readInt(); // 应该是1024
            int cols = dis.readInt(); // 应该是2048
            float[][] matrix = new float[rows][cols];
            for (int i = 0; i < rows; i++) {
                for (int j = 0; j < cols; j++) {
                    matrix[i][j] = dis.readFloat();
                }
            }
            return matrix;

        } catch (IOException e) {
            throw new RuntimeException("加载 projectionMatrix.bin 失败", e);
        }
    }

    public static float[] transform(float[] input) {
        if (input.length != inputDim) {
            throw new IllegalArgumentException("输入向量维度必须为 2048");
        }

        float[] output = new float[targetDim];
        for (int i = 0; i < targetDim; i++) {
            float sum = 0f;
            for (int j = 0; j < inputDim; j++) {
                sum += projectionMatrix[i][j] * input[j];
            }
            output[i] = sum;
        }
        return output;
    }
}
