package com.yuqiangdede.common.util;

import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Random;

public class GenerateProjectionMatrix {
    public static void main(String[] args) throws IOException {
        int rows = 1024;
        int cols = 2048;
        float[][] matrix = new float[rows][cols];
        Random random = new Random(42L);
        float scale = (float) (1.0 / Math.sqrt(rows));

        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                matrix[i][j] = (float) (random.nextGaussian() * scale);
            }
        }

        try (DataOutputStream out = new DataOutputStream(new FileOutputStream("projectionMatrix.bin"))) {
            out.writeInt(rows);
            out.writeInt(cols);
            for (int i = 0; i < rows; i++) {
                for (int j = 0; j < cols; j++) {
                    out.writeFloat(matrix[i][j]);
                }
            }
        }

        System.out.println("projectionMatrix.bin 写入成功");
    }
}
