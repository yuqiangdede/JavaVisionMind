package com.yuqiangdede.common.util;

import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import static com.yuqiangdede.common.Constant.MATRIX_PATH;

public class RandomProjectionUtils {
    private static final float[][] projectionMatrix = loadMatrix();
    private static final int INPUT_DIM = 2048;
    private static final int TARGET_DIM = 1024;

    private static float[][] loadMatrix() {
        try (InputStream is = new FileInputStream(MATRIX_PATH);
             DataInputStream dis = new DataInputStream(is)) {
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
            throw new RuntimeException("Failed to load projectionMatrix.bin", e);
        }
    }

    public static float[] transform(float[] input) {
        if (input.length != INPUT_DIM) {
            throw new IllegalArgumentException("Input vector dimension must be 2048");
        }

        float[] output = new float[TARGET_DIM];
        for (int i = 0; i < TARGET_DIM; i++) {
            float sum = 0f;
            for (int j = 0; j < INPUT_DIM; j++) {
                sum += projectionMatrix[i][j] * input[j];
            }
            output[i] = sum;
        }
        return output;
    }
}
