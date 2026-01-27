package com.yuqiangdede.yolo.util.yolo;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import com.yuqiangdede.yolo.config.Constant;
import com.yuqiangdede.yolo.dto.output.YoloDetectionResult;
import lombok.extern.slf4j.Slf4j;
import org.opencv.core.Mat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

@Slf4j
public class YoloV26DetUtil extends YoloBaseUtil {

    private static volatile Model yolomodel;

    private static Model getYoloModel() {
        if (yolomodel == null) {
            synchronized (YoloV26DetUtil.class) {
                if (yolomodel == null) {
                    try {
                        yolomodel = load(Constant.YOLO_ONNX_PATH, Constant.YOLO_NMS_ENABLED);
                    } catch (OrtException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }
        return yolomodel;
    }


    public static YoloDetectionResult predictor(Mat mat, Float conf) {
        return predictor(mat, getYoloModel(), conf);
    }

    private static YoloDetectionResult predictor(Mat src, Model model, Float conf) {
        try (OnnxTensor tensor = transferTensor(src, model)) {
            try (OrtSession.Result result = model.session.run(Collections.singletonMap("images", tensor))) {
                try (OnnxTensor res = (OnnxTensor) result.get(0)) {
                    float[][] data = ((float[][][]) res.getValue())[0];
                    float scaleW = (float) Math.max(src.width(), src.height()) / model.netWidth;
                    float scaleH = (float) Math.max(src.width(), src.height()) / model.netHeight;
                    float threshold = conf == null ? model.confThreshold : conf;

                    List<ArrayList<Float>> boxes = new ArrayList<>();
                    for (float[] row : data) {
                        float score = row[4];
                        if (score <= threshold) {
                            continue;
                        }
                        float x1 = row[0] * scaleW;
                        float y1 = row[1] * scaleH;
                        float x2 = row[2] * scaleW;
                        float y2 = row[3] * scaleH;
                        float classId = row[5];
                        boxes.add(new ArrayList<>(Arrays.asList(x1, y1, x2, y2, score, classId)));
                    }

                    List<List<Float>> boxesAfterNms = boxes.stream()
                            .map(List::copyOf)
                            .toList();
                    if (model.nmsEnabled) {
                        boxesAfterNms = NMS(model, boxes).stream()
                                .map(List::copyOf)
                                .toList();
                    }

                    return new YoloDetectionResult(boxesAfterNms, Map.copyOf(model.names));
                }
            }
        } catch (OrtException e) {
            log.error("detect error", e);
            throw new RuntimeException(e);
        }
    }

    private static List<ArrayList<Float>> NMS(Model model, List<ArrayList<Float>> boxes) {
        int[] indexs = new int[boxes.size()];
        Arrays.fill(indexs, 1);

        for (int cur = 0; cur < boxes.size(); cur++) {
            if (indexs[cur] == 0) {
                continue;
            }
            ArrayList<Float> curMaxConf = boxes.get(cur);

            for (int i = cur + 1; i < boxes.size(); i++) {
                if (indexs[i] == 0) {
                    continue;
                }
                float classIndex = boxes.get(i).get(5);
                if (classIndex == curMaxConf.get(5)) {
                    float x1 = curMaxConf.get(0);
                    float y1 = curMaxConf.get(1);
                    float x2 = curMaxConf.get(2);
                    float y2 = curMaxConf.get(3);
                    float x3 = boxes.get(i).get(0);
                    float y3 = boxes.get(i).get(1);
                    float x4 = boxes.get(i).get(2);
                    float y4 = boxes.get(i).get(3);

                    if (x1 > x4 || x2 < x3 || y1 > y4 || y2 < y3) {
                        continue;
                    }

                    float intersectionWidth = Math.max(x1, x3) - Math.min(x2, x4);
                    float intersectionHeight = Math.max(y1, y3) - Math.min(y2, y4);
                    float intersectionArea = Math.max(0, intersectionWidth * intersectionHeight);
                    float unionArea = (x2 - x1) * (y2 - y1) + (x4 - x3) * (y4 - y3) - intersectionArea;
                    float iou = intersectionArea / unionArea;

                    if (iou > model.nmsThreshold) {
                        if (boxes.get(i).get(4) <= curMaxConf.get(4)) {
                            indexs[i] = 0;
                        } else {
                            indexs[cur] = 0;
                        }
                    }
                }
            }
        }

        List<ArrayList<Float>> resBoxes = new LinkedList<>();
        for (int index = 0; index < indexs.length; index++) {
            if (indexs[index] == 1) {
                resBoxes.add(boxes.get(index));
            }
        }

        return resBoxes;
    }
}
