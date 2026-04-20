package com.yuqiangdede.common.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ArrayUtilTest {

    @Test
    void floatToDouble_convertsValues() {
        assertArrayEquals(new double[]{1.5d, -2.25d}, ArrayUtil.floatToDouble(new float[]{1.5f, -2.25f}));
    }

    @Test
    void floatToDouble_returnsNullWhenInputIsNull() {
        assertNull(ArrayUtil.floatToDouble(null));
    }

    @Test
    void doubleToFloat_convertsValues() {
        assertArrayEquals(new float[]{1.5f, -2.25f}, ArrayUtil.doubleToFloat(new double[]{1.5d, -2.25d}));
    }

    @Test
    void doubleToFloat_returnsNullWhenInputIsNull() {
        assertNull(ArrayUtil.doubleToFloat(null));
    }

    @Test
    void division_scalesEachElement() {
        assertArrayEquals(new float[]{2.0f, -4.0f}, ArrayUtil.division(new float[]{4.0f, -8.0f}, 2.0f));
    }

    @Test
    void matrixNorm_usesEuclideanNorm() {
        assertEquals(5.0d, ArrayUtil.matrixNorm(new double[]{3.0d, 4.0d}), 1e-9);
    }
}
