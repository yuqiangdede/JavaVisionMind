package com.yuqiangdede.common.util;

import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MathUtilTest {

    @Test
    void createVectorAndMatrix_convertsInput() {
        RealVector vector = MathUtil.createVector(new double[]{1.0d, 2.0d});
        RealMatrix matrix = MathUtil.createMatrix(new Double[][]{{1.0d, 2.0d}, {3.0d, 4.0d}});

        assertArrayEquals(new double[]{1.0d, 2.0d}, vector.toArray());
        assertEquals(2, matrix.getRowDimension());
        assertEquals(2, matrix.getColumnDimension());
        assertEquals(4.0d, matrix.getEntry(1, 1), 1e-9);
    }

    @Test
    void scalarOperations_andStackingWork() {
        RealMatrix left = MathUtil.createMatrix(new double[][]{{1.0d, 2.0d}, {3.0d, 4.0d}});
        RealMatrix right = MathUtil.createMatrix(new double[][]{{5.0d, 6.0d}, {7.0d, 8.0d}});

        assertEquals(3.0d, MathUtil.scalarAdd(left, 2.0d).getEntry(0, 0), 1e-9);
        assertEquals(-1.0d, MathUtil.scalarSub(left, 2.0d).getEntry(0, 0), 1e-9);
        assertEquals(2.0d, MathUtil.scalarMultiply(left, 2.0d).getEntry(0, 0), 1e-9);
        assertEquals(2.0d, MathUtil.scalarDivision(left, 0.5d).getEntry(0, 0), 1e-9);
        assertEquals(19.0d, MathUtil.dotProduct(left, right).getEntry(0, 0), 1e-9);

        RealMatrix hstack = MathUtil.hstack(left, right);
        RealMatrix vstack = MathUtil.vstack(left, right);
        assertEquals(4, hstack.getColumnDimension());
        assertEquals(4, vstack.getRowDimension());
    }

    @Test
    void meanStdAndFlatMatrix_computeExpectedValues() {
        RealMatrix matrix = MathUtil.createMatrix(new double[][]{{1.0d, 2.0d}, {3.0d, 4.0d}});

        assertArrayEquals(new double[]{2.0d, 3.0d}, MathUtil.mean(matrix, 0).toArray(), 1e-9);
        assertArrayEquals(new double[]{1.5d, 3.5d}, MathUtil.mean(matrix, 1).toArray(), 1e-9);
        assertEquals(Math.sqrt(1.25d), MathUtil.std(matrix), 1e-9);
        assertArrayEquals(new double[]{1.0d, 3.0d, 2.0d, 4.0d}, MathUtil.flatMatrix(matrix, 0).toArray(), 1e-9);
        assertArrayEquals(new double[]{1.0d, 2.0d, 3.0d, 4.0d}, MathUtil.flatMatrix(matrix, 1).toArray(), 1e-9);
    }

    @Test
    void similarityTransform_supportsIdentityAndExplicitParameters() {
        RealMatrix identity = MathUtil.similarityTransform((RealMatrix) null, null, null, null);
        RealMatrix explicit = MathUtil.similarityTransform(null, 2.0d, 0.0d, MathUtil.createVector(new double[]{3.0d, 4.0d}));
        RealMatrix preserved = MathUtil.similarityTransform(MathUtil.createMatrix(new double[][]{{1.0d, 2.0d, 3.0d}, {4.0d, 5.0d, 6.0d}, {7.0d, 8.0d, 9.0d}}), null, null, null);

        assertArrayEquals(new double[]{1.0d, 0.0d, 0.0d}, identity.getRow(0), 1e-9);
        assertEquals(3.0d, explicit.getEntry(0, 2), 1e-9);
        assertEquals(4.0d, explicit.getEntry(1, 2), 1e-9);
        assertEquals(9.0d, preserved.getEntry(2, 2), 1e-9);
    }

    @Test
    void similarityTransform_rejectsInvalidCombinationAndShape() {
        assertThrows(RuntimeException.class, () -> MathUtil.similarityTransform(
                MathUtil.createMatrix(new double[][]{{1.0d, 2.0d}, {3.0d, 4.0d}}),
                1.0d,
                0.0d,
                null
        ));

        assertThrows(RuntimeException.class, () -> MathUtil.similarityTransform(
                MathUtil.createMatrix(new double[][]{{1.0d, 2.0d}, {3.0d, 4.0d}}),
                null,
                null,
                null
        ));
    }
}
