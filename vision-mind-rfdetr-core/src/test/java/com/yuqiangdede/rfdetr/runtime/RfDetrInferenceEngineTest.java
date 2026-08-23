package com.yuqiangdede.rfdetr.runtime;

import com.yuqiangdede.common.dto.output.Box;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RfDetrInferenceEngineTest {

    @Test
    void modelContract_usesRfDetrSmallResolution() {
        assertArrayEquals(new long[]{1, 3, 512, 512}, RfDetrInferenceEngine.INPUT_SHAPE);
        assertArrayEquals(new long[]{1, 300, 4}, RfDetrInferenceEngine.DETS_SHAPE);
        assertArrayEquals(new long[]{1, 300, 91}, RfDetrInferenceEngine.LABELS_SHAPE);
    }

    @Test
    void decode_preservesSparseCocoIdsAndIgnoresHoles() {
        float[][] dets = {
                {0.5f, 0.5f, 0.4f, 0.4f},
                {0.2f, 0.2f, 0.2f, 0.2f}
        };
        float[][] labels = new float[2][91];
        Arrays.stream(labels).forEach(row -> Arrays.fill(row, -20.0f));
        labels[0][0] = 20.0f;
        labels[0][90] = 4.0f;
        labels[0][18] = 3.0f;
        labels[1][3] = 2.0f;

        List<Box> boxes = RfDetrInferenceEngine.decode(dets, labels, 100, 50, 0.3f, CocoClassNames.standard());

        assertEquals(3, boxes.size());
        assertEquals(90, boxes.get(0).getType());
        assertEquals("toothbrush", boxes.get(0).getTypeName());
        assertEquals(18, boxes.get(1).getType());
        assertEquals("dog", boxes.get(1).getTypeName());
        assertEquals(3, boxes.get(2).getType());
        assertEquals(30.0f, boxes.get(0).getX1(), 1e-5f);
        assertEquals(70.0f, boxes.get(0).getX2(), 1e-5f);
    }

    @Test
    void decode_usesStrictThresholdAndDoesNotApplyNms() {
        float[][] dets = {
                {0.5f, 0.5f, 1.4f, 1.4f},
                {0.5f, 0.5f, 1.4f, 1.4f}
        };
        float[][] labels = new float[2][91];
        Arrays.stream(labels).forEach(row -> Arrays.fill(row, -20.0f));
        labels[0][1] = 0.0f;
        labels[0][3] = 2.0f;
        labels[1][3] = 2.0f;

        List<Box> boxes = RfDetrInferenceEngine.decode(dets, labels, 100, 100, 0.5f, CocoClassNames.standard());

        assertEquals(2, boxes.size());
        assertTrue(boxes.stream().allMatch(box -> box.getType() == 3));
        assertTrue(boxes.stream().allMatch(box -> box.getX1() == 0.0f && box.getX2() == 100.0f));
    }
}
