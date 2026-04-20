package com.yuqiangdede.common.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VectorUtilTest {

    @Test
    void normalizeVector_returnsUnitVector() {
        assertArrayEquals(new float[]{0.6f, 0.8f}, VectorUtil.normalizeVector(new float[]{3.0f, 4.0f}), 1e-6f);
    }

    @Test
    void normalizeVector_returnsOriginalZeroVector() {
        float[] zero = new float[]{0.0f, 0.0f};
        assertSame(zero, VectorUtil.normalizeVector(zero));
    }

    @Test
    void calculateCosineSimilarity_returnsExpectedValue() {
        assertEquals(0.0d, VectorUtil.calculateCosineSimilarity(new float[]{1.0f, 0.0f}, new float[]{0.0f, 1.0f}), 1e-9);
    }

    @Test
    void calculateCosineSimilarity_rejectsInvalidInputs() {
        assertThrows(IllegalArgumentException.class, () -> VectorUtil.calculateCosineSimilarity(
                new float[]{1.0f},
                new float[]{1.0f, 2.0f}
        ));
        assertThrows(IllegalArgumentException.class, () -> VectorUtil.calculateCosineSimilarity(
                new float[]{0.0f},
                new float[]{1.0f}
        ));
    }
}
