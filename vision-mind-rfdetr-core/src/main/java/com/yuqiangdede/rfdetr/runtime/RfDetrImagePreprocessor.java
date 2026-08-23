package com.yuqiangdede.rfdetr.runtime;

import java.awt.image.BufferedImage;

public final class RfDetrImagePreprocessor {

    private static final float[] MEAN = {0.485f, 0.456f, 0.406f};
    private static final float[] STD = {0.229f, 0.224f, 0.225f};

    private RfDetrImagePreprocessor() {
    }

    /**
     * Matches RF-DETR ONNX preprocessing: RGB, half-pixel bilinear resize, ImageNet normalization and NCHW.
     */
    public static float[] preprocess(BufferedImage source, int targetWidth, int targetHeight) {
        if (source == null || source.getWidth() <= 0 || source.getHeight() <= 0) {
            throw new IllegalArgumentException("image is null or empty");
        }
        if (targetWidth <= 0 || targetHeight <= 0) {
            throw new IllegalArgumentException("target image size must be positive");
        }

        int sourceWidth = source.getWidth();
        int sourceHeight = source.getHeight();
        float[] result = new float[3 * targetWidth * targetHeight];
        int planeSize = targetWidth * targetHeight;
        for (int y = 0; y < targetHeight; y++) {
            float sourceY = ((y + 0.5f) * sourceHeight / targetHeight) - 0.5f;
            int y0 = clamp((int) Math.floor(sourceY), 0, sourceHeight - 1);
            int y1 = clamp(y0 + 1, 0, sourceHeight - 1);
            float yWeight = sourceY - (float) Math.floor(sourceY);
            for (int x = 0; x < targetWidth; x++) {
                float sourceX = ((x + 0.5f) * sourceWidth / targetWidth) - 0.5f;
                int x0 = clamp((int) Math.floor(sourceX), 0, sourceWidth - 1);
                int x1 = clamp(x0 + 1, 0, sourceWidth - 1);
                float xWeight = sourceX - (float) Math.floor(sourceX);
                int pixelIndex = y * targetWidth + x;
                for (int channel = 0; channel < 3; channel++) {
                    float value = bilinear(source, x0, y0, x1, y1, xWeight, yWeight, channel) / 255.0f;
                    result[channel * planeSize + pixelIndex] = (value - MEAN[channel]) / STD[channel];
                }
            }
        }
        return result;
    }

    private static float bilinear(BufferedImage image, int x0, int y0, int x1, int y1,
                                  float xWeight, float yWeight, int channel) {
        float top = channel(image.getRGB(x0, y0), channel) * (1.0f - xWeight)
                + channel(image.getRGB(x1, y0), channel) * xWeight;
        float bottom = channel(image.getRGB(x0, y1), channel) * (1.0f - xWeight)
                + channel(image.getRGB(x1, y1), channel) * xWeight;
        return top * (1.0f - yWeight) + bottom * yWeight;
    }

    private static int channel(int rgb, int channel) {
        return switch (channel) {
            case 0 -> (rgb >> 16) & 0xFF;
            case 1 -> (rgb >> 8) & 0xFF;
            case 2 -> rgb & 0xFF;
            default -> throw new IllegalArgumentException("unsupported RGB channel: " + channel);
        };
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
