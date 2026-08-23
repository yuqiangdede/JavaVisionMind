package com.yuqiangdede.yolo.util.yolo;

import com.yuqiangdede.yolo.dto.output.DepthEstimationResult;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class YoloV26DepthUtilTest {

    private static final float EPSILON = 1.0e-5f;

    @Test
    void preprocess_centersLetterboxAndProducesRgbChw() {
        BufferedImage image = new BufferedImage(4, 2, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                image.setRGB(x, y, Color.RED.getRGB());
            }
        }

        YoloV26DepthUtil.LetterboxInput input = YoloV26DepthUtil.preprocess(image, 4, 4);

        assertEquals(0, input.left());
        assertEquals(1, input.top());
        assertEquals(0, input.right());
        assertEquals(1, input.bottom());
        assertEquals(114f / 255f, input.chw().get(0), EPSILON);
        assertEquals(1f, input.chw().get(4), EPSILON);
        assertEquals(0f, input.chw().get(16 + 4), EPSILON);
        assertEquals(0f, input.chw().get(32 + 4), EPSILON);
    }

    @Test
    void restoreDepthMap_removesLetterboxBeforeRestoringOriginalSize() {
        BufferedImage image = new BufferedImage(4, 2, BufferedImage.TYPE_INT_RGB);
        YoloV26DepthUtil.LetterboxInput input = YoloV26DepthUtil.preprocess(image, 4, 4);
        float[][] modelDepth = {
                {99f, 99f, 99f, 99f},
                {1f, 1f, 1f, 1f},
                {2f, 2f, 2f, 2f},
                {99f, 99f, 99f, 99f}
        };

        float[][] restored = YoloV26DepthUtil.restoreDepthMap(modelDepth, input);

        assertArrayEquals(new float[]{1f, 1f, 1f, 1f}, restored[0], EPSILON);
        assertArrayEquals(new float[]{2f, 2f, 2f, 2f}, restored[1], EPSILON);
    }

    @Test
    void preprocess_assignsOddPaddingLikeUltralytics() {
        BufferedImage image = new BufferedImage(5, 3, BufferedImage.TYPE_INT_RGB);

        YoloV26DepthUtil.LetterboxInput input = YoloV26DepthUtil.preprocess(image, 7, 7);

        assertEquals(0, input.left());
        assertEquals(1, input.top());
        assertEquals(0, input.right());
        assertEquals(2, input.bottom());
    }

    @Test
    void resizeDepthMap_usesHalfPixelBilinearInterpolation() {
        float[][] source = {
                {1f, 2f},
                {3f, 4f}
        };

        float[][] resized = YoloV26DepthUtil.resizeDepthMap(source, 3, 3);

        assertEquals(1f, resized[0][0], EPSILON);
        assertEquals(2.5f, resized[1][1], EPSILON);
        assertEquals(4f, resized[2][2], EPSILON);
    }

    @Test
    void buildResult_reportsStatisticsAndSanitizesInvalidValues() {
        float[][] depths = {
                {1f, 2f},
                {Float.NaN, -1f}
        };

        DepthEstimationResult result = YoloV26DepthUtil.buildResult(depths);

        assertEquals(2, result.getWidth());
        assertEquals(2, result.getHeight());
        assertEquals(2, result.getValidPixelCount());
        assertEquals(1f, result.getMinDepth(), EPSILON);
        assertEquals(2f, result.getMaxDepth(), EPSILON);
        assertEquals(1.5f, result.getMeanDepth(), EPSILON);
        assertEquals(1.5f, result.getMedianDepth(), EPSILON);
        assertArrayEquals(new float[]{1f, 2f, 0f, 0f}, result.getDepthMap(), EPSILON);
    }

    @Test
    void renderPreview_rejectsMetricModeWithoutFixedRange() {
        YoloV26DepthUtil util = new YoloV26DepthUtil(Path.of("missing.onnx"));
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        DepthEstimationResult result = YoloV26DepthUtil.buildResult(new float[][]{{2f}});

        assertThrows(IllegalArgumentException.class,
                () -> util.renderPreview(image, result, "metric", null, null));
    }
}
