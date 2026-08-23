package com.yuqiangdede.rfdetr.runtime;

import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RfDetrImagePreprocessorTest {

    @Test
    void preprocess_convertsRgbToNormalizedNchw() {
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, new Color(255, 0, 0).getRGB());

        float[] result = RfDetrImagePreprocessor.preprocess(image, 1, 1);

        assertEquals((1.0f - 0.485f) / 0.229f, result[0], 1e-6f);
        assertEquals((0.0f - 0.456f) / 0.224f, result[1], 1e-6f);
        assertEquals((0.0f - 0.406f) / 0.225f, result[2], 1e-6f);
    }

    @Test
    void preprocess_usesNchwPlaneOrderingForNonSquareOutput() {
        BufferedImage image = new BufferedImage(2, 1, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, new Color(255, 0, 0).getRGB());
        image.setRGB(1, 0, new Color(0, 255, 0).getRGB());

        float[] result = RfDetrImagePreprocessor.preprocess(image, 2, 1);

        assertEquals((1.0f - 0.485f) / 0.229f, result[0], 1e-6f);
        assertEquals((0.0f - 0.485f) / 0.229f, result[1], 1e-6f);
        assertEquals((0.0f - 0.456f) / 0.224f, result[2], 1e-6f);
        assertEquals((1.0f - 0.456f) / 0.224f, result[3], 1e-6f);
    }
}
