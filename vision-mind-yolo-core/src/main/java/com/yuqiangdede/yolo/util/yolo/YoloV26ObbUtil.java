package com.yuqiangdede.yolo.util.yolo;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import com.yuqiangdede.common.dto.Point;
import com.yuqiangdede.yolo.config.Constant;
import com.yuqiangdede.yolo.dto.output.ObbDetection;
import lombok.extern.slf4j.Slf4j;
import org.opencv.core.Mat;
import org.opencv.core.MatOfFloat;
import org.opencv.core.MatOfInt;
import org.opencv.core.MatOfRotatedRect;
import org.opencv.core.RotatedRect;
import org.opencv.core.Size;
import org.opencv.dnn.Dnn;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
public class YoloV26ObbUtil extends YoloBaseUtil {

    private static final Model yoloObbModel;

    static {
        try {
            yoloObbModel = load(Constant.YOLO_OBB_ONNX_PATH, Constant.YOLO_OBB_NMS_ENABLED);
        } catch (OrtException e) {
            throw new RuntimeException(e);
        }
    }

    public static List<ObbDetection> predictor(Mat src, Float conf) {
        try (OnnxTensor tensor = transferTensor(src, yoloObbModel);
             OrtSession.Result result = yoloObbModel.session.run(Collections.singletonMap("images", tensor))) {
            try (OnnxTensor res = (OnnxTensor) result.get(0)) {
                float[][] data = ((float[][][]) res.getValue())[0];
                float scaleW = (float) Math.max(src.width(), src.height()) / yoloObbModel.netWidth;
                float scaleH = (float) Math.max(src.width(), src.height()) / yoloObbModel.netHeight;
                float threshold = conf == null ? yoloObbModel.confThreshold : conf;

                List<ObbCandidate> candidates = new ArrayList<>();
                for (float[] pred : data) {
                    // output0 format (Ultralytics end2end OBB): [x, y, w, h, conf, cls, angle]
                    float score = pred[4];
                    if (score < threshold) {
                        continue;
                    }

                    float centerX = pred[0] * scaleW;
                    float centerY = pred[1] * scaleH;
                    float width = pred[2] * scaleW;
                    float height = pred[3] * scaleH;
                    float angle = pred[6];
                    int classId = Math.round(pred[5]);

                    String className = Optional.ofNullable(yoloObbModel.names.get(classId))
                            .map(name -> name.replace("'", ""))
                            .orElse(null);
                    List<Point> points = buildPoints(centerX, centerY, width, height, angle);

                    ObbDetection detection = new ObbDetection(centerX, centerY, width, height, angle, score, classId, className, points);
                    RotatedRect rect = new RotatedRect(new org.opencv.core.Point(centerX, centerY), new Size(width, height),
                            (float) Math.toDegrees(angle));
                    candidates.add(new ObbCandidate(detection, rect, score, classId));
                }

                return applyNms(candidates, !Constant.YOLO_OBB_NMS_ENABLED);
            }
        } catch (OrtException e) {
            log.error("obb detect error", e);
            throw new RuntimeException(e);
        }
    }

    private static List<Point> buildPoints(float centerX, float centerY, float width, float height, float angle) {
        float halfW = width / 2f;
        float halfH = height / 2f;
        float cos = (float) Math.cos(angle);
        float sin = (float) Math.sin(angle);

        float[][] corners = new float[][]{
                {halfW, halfH},
                {-halfW, halfH},
                {-halfW, -halfH},
                {halfW, -halfH}
        };

        List<Point> points = new ArrayList<>(4);
        for (float[] corner : corners) {
            float dx = corner[0];
            float dy = corner[1];
            float x = centerX + dx * cos - dy * sin;
            float y = centerY + dx * sin + dy * cos;
            points.add(new Point(x, y));
        }
        return points;
    }

    private static List<ObbDetection> applyNms(List<ObbCandidate> candidates, boolean skipNms) {
        if (skipNms) {
            List<ObbDetection> results = new ArrayList<>();
            for (ObbCandidate candidate : candidates) {
                results.add(candidate.detection);
            }
            return results;
        }

        Map<Integer, List<Integer>> classGroups = new HashMap<>();
        for (int i = 0; i < candidates.size(); i++) {
            classGroups.computeIfAbsent(candidates.get(i).classId, key -> new ArrayList<>()).add(i);
        }

        List<ObbDetection> results = new ArrayList<>();
        for (Map.Entry<Integer, List<Integer>> entry : classGroups.entrySet()) {
            List<Integer> indices = entry.getValue();
            RotatedRect[] rects = new RotatedRect[indices.size()];
            float[] scores = new float[indices.size()];

            for (int i = 0; i < indices.size(); i++) {
                ObbCandidate candidate = candidates.get(indices.get(i));
                rects[i] = candidate.rect;
                scores[i] = candidate.score;
            }

            MatOfRotatedRect rectMat = new MatOfRotatedRect(rects);
            MatOfFloat scoreMat = new MatOfFloat(scores);
            MatOfInt keep = new MatOfInt();
            Dnn.NMSBoxesRotated(rectMat, scoreMat, 0.0f, Constant.NMS_THRESHOLD, keep);

            for (int keptIndex : keep.toArray()) {
                results.add(candidates.get(indices.get(keptIndex)).detection);
            }
        }

        return results;
    }

    private static class ObbCandidate {
        private final ObbDetection detection;
        private final RotatedRect rect;
        private final float score;
        private final int classId;

        private ObbCandidate(ObbDetection detection, RotatedRect rect, float score, int classId) {
            this.detection = detection;
            this.rect = rect;
            this.score = score;
            this.classId = classId;
        }
    }
}
