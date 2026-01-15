package com.yuqiangdede.yolo.util.yolo;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import com.yuqiangdede.yolo.config.Constant;
import com.yuqiangdede.yolo.dto.output.YoloPoseDetectionResult;
import org.opencv.core.Mat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class YoloV26PoseUtil extends YoloBaseUtil {

    static final Model yoloposemodel;

    static {
        try {
            yoloposemodel = load(Constant.YOLO_POSE_ONNX_PATH, Constant.YOLO_POSE_NMS_ENABLED);
        } catch (OrtException e) {
            throw new RuntimeException(e);
        }
    }

    public static YoloPoseDetectionResult predictor(Mat mat, Float conf) {
        return predictor(mat, yoloposemodel, conf);
    }

    private static YoloPoseDetectionResult predictor(Mat src, Model model, Float conf) {
        try (OnnxTensor tensor = transferTensor(src, model)) {
            try (OrtSession.Result result = model.session.run(Collections.singletonMap("images", tensor))) {
                try (OnnxTensor res = (OnnxTensor) result.get(0)) {
                    float[][] data = ((float[][][]) res.getValue())[0];
                    float scaleW = (float) Math.max(src.width(), src.height()) / model.netWidth;
                    float scaleH = (float) Math.max(src.width(), src.height()) / model.netHeight;
                    float threshold = conf == null ? model.confThreshold : conf;

                    List<List<Float>> boxes = new ArrayList<>();
                    for (float[] row : data) {
                        float score = row[4];
                        if (score <= threshold) {
                            continue;
                        }

                        List<Float> values = new ArrayList<>(56);
                        float x1 = row[0] * scaleW;
                        float y1 = row[1] * scaleH;
                        float x2 = row[2] * scaleW;
                        float y2 = row[3] * scaleH;
                        values.add(x1);
                        values.add(y1);
                        values.add(x2);
                        values.add(y2);
                        values.add(score);

                        for (int i = 6; i < row.length; i += 3) {
                            float kptX = row[i] * scaleW;
                            float kptY = row[i + 1] * scaleH;
                            float kptScore = row[i + 2];
                            values.add(kptX);
                            values.add(kptY);
                            values.add(kptScore);
                        }
                        boxes.add(values);
                    }

                    return new YoloPoseDetectionResult(boxes);
                }
            }
        } catch (OrtException e) {
            throw new RuntimeException(e);
        }
    }
}
