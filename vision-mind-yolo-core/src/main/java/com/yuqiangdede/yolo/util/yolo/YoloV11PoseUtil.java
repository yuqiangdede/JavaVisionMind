package com.yuqiangdede.yolo.util.yolo;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import com.yuqiangdede.yolo.config.Constant;
import org.opencv.core.Mat;

import java.util.*;

public class YoloV11PoseUtil extends YoloUtil {

    static final Model yoloposemodel;

    static {
        try {
            yoloposemodel = load(Constant.YOLO_POSE_ONNX_PATH);
        } catch (OrtException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 用来指定使用的模型，也可以直接调用predictor(Mat src, Model model) 但是需要自己加载模型
     *
     * @param mat  图片矩阵
     * @param conf 置信度
     * @return 检测结果
     */
    public static YoloPoseDetectionResult predictor(Mat mat, Float conf) {
        return predictor(mat, yoloposemodel, conf);
    }

    /**
     * @param src   图片矩阵
     * @param model 模型
     * @param conf  置信度
     * @return 检测结果
     */
    private static YoloPoseDetectionResult predictor(Mat src, Model model, Float conf) {
        // pretreatment to OnnxTensor
        try (OnnxTensor tensor = transferTensor(src, model)) {
            try (OrtSession.Result result = yoloposemodel.session.run(Collections.singletonMap("images", tensor))) {
                try (OnnxTensor res = (OnnxTensor) result.get(0)) {

                    float[][] data = ((float[][][]) res.getValue())[0];
                    /*
                     people      X1    X2    X3    ... Xn
                                 Y1    Y2    Y3    ... Yn
                                 W1    W2    W3    ... Wn
                                 H1    H2    H3    ... Hn
                                 C1    C2    C3    ... Cn    -- confidence for people
                     point1      X1-1  X2-1  X3-1  ... Xn-1  -- X(people num)-(point num)
                                 Y1-1  Y2-1  Y3-1  ... Yn-1
                                 C1-1  C2-1  C3-1  ... Cn-1  -- confidence for 1st point
                     point2      X1-2  X2-2  X3-2  ... Xn-2
                                 Y1-2  Y2-2  Y3-2  ... Yn-2
                                 C1-2  C2-2  C3-2  ... Cn-2  -- confidence for 2nd point
                     ............................
                     point17     X1-17 X2-17 X3-17 ... Xn-17
                                 Y1-17 Y2-17 Y3-17 ... Yn-17
                                 C1-17 C2-17 C3-17 ... Cn-17 -- confidence for 17th point


                     transpositionData
                     people1     X1 Y1 W1 H1 C1 X1-1 Y1-1 C1-1 X1-2 Y1-2 C1-2 .......... X1-17 Y1-17 C1-17 -- length is 56
                     people2     X2 Y2 W2 H2 C2 X2-1 Y2-1 C2-1 X2-2 Y2-2 C2-2 .......... X2-17 Y2-17 C2-17
                     ......................................................................
                     peoplem     Xm Ym Wm Hm Cm Xm-1 Ym-1 Cm-1 Xm-2 Ym-2 Cm-2 .......... Xm-17 Ym-17 Cm-17

                     */

                    Float[][] transpositionData = new Float[data[0].length][56];
                    // put X1 Y1 W1 H1 C1
                    for (int i = 0; i < 56; i++) {
                        for (int j = 0; j < data[0].length; j++) {
                            transpositionData[j][i] = data[i][j];
                        }
                    }


                    List<ArrayList<Float>> boxes = new ArrayList<>();
                    // Since the image used for prediction is resized, the coordinates returned are relative to the resized image.
                    // Therefore, the final coordinates need to be restored to the original scale.
                    float scaleW = (float) Math.max(src.width(), src.height()) / yoloposemodel.netWidth;
                    float scaleH = (float) Math.max(src.width(), src.height()) / yoloposemodel.netHeight;
                    // Apply confidence threshold, convert xywh to xyxy, and restore the resized coordinates.
                    for (Float[] d : transpositionData) {
                        // Apply confidence threshold
                        if (d[4] > (conf == null ? model.confThreshold : conf)) {
                            // xywh to xyxy
                            d[0] = d[0] - d[2] / 2;
                            d[1] = d[1] - d[3] / 2;
                            d[2] = d[0] + d[2];
                            d[3] = d[1] + d[3];
                            // Restore the resized coordinates to obtain the original coordinates
                            d[0] = d[0] * scaleW;
                            d[1] = d[1] * scaleH;
                            d[2] = d[2] * scaleW;
                            d[3] = d[3] * scaleH;
                            d[5] = d[5] * scaleW;
                            d[6] = d[6] * scaleH;
                            d[8] = d[8] * scaleW;
                            d[9] = d[9] * scaleH;
                            d[11] = d[11] * scaleW;
                            d[12] = d[12] * scaleH;
                            d[14] = d[14] * scaleW;
                            d[15] = d[15] * scaleH;
                            d[17] = d[17] * scaleW;
                            d[18] = d[18] * scaleH;
                            d[20] = d[20] * scaleW;
                            d[21] = d[21] * scaleH;
                            d[23] = d[23] * scaleW;
                            d[24] = d[24] * scaleH;
                            d[26] = d[26] * scaleW;
                            d[27] = d[27] * scaleH;
                            d[29] = d[29] * scaleW;
                            d[30] = d[30] * scaleH;
                            d[32] = d[32] * scaleW;
                            d[33] = d[33] * scaleH;
                            d[35] = d[35] * scaleW;
                            d[36] = d[36] * scaleH;
                            d[38] = d[38] * scaleW;
                            d[39] = d[39] * scaleH;
                            d[41] = d[41] * scaleW;
                            d[42] = d[42] * scaleH;
                            d[44] = d[44] * scaleW;
                            d[45] = d[45] * scaleH;
                            d[47] = d[47] * scaleW;
                            d[48] = d[48] * scaleH;
                            d[50] = d[50] * scaleW;
                            d[51] = d[51] * scaleH;
                            d[53] = d[53] * scaleW;
                            d[54] = d[54] * scaleH;
                            ArrayList<Float> box = new ArrayList<>(Arrays.asList(d));
                            boxes.add(box);
                        }
                    }

                    List<List<Float>> boxesAfterNMS = NMS(yoloposemodel, boxes).stream()
                            .map(List::copyOf)
                            .toList();
                    return new YoloPoseDetectionResult(boxesAfterNMS);
                }
            }
        } catch (OrtException e) {
            throw new RuntimeException(e);
        }
    }


    /**
     * 执行非极大值抑制（NMS）算法，过滤重叠的边界框。
     *
     * @param model 包含NMS阈值的模型对象
     * @param boxes 包含多个边界框的列表，每个边界框是一个包含四个浮点数（x1, y1, x2, y2）的ArrayList
     * @return 过滤后的边界框列表，每个边界框是一个包含四个浮点数（x1, y1, x2, y2）的ArrayList
     */
    static List<ArrayList<Float>> NMS(Model model, List<ArrayList<Float>> boxes) {
        int[] indexs = new int[boxes.size()];
        Arrays.fill(indexs, 1); // Initialize the indexs array with all elements set to 1, indicating all boxes are initially kept

        for (int cur = 0; cur < boxes.size(); cur++) {
            // Skip if the current box is marked for removal
            if (indexs[cur] == 0) {
                continue;
            }
            ArrayList<Float> curMaxConf = boxes.get(cur);

            // Iterate through the boxes after the current one
            for (int i = cur + 1; i < boxes.size(); i++) {
                // Skip if the current box (being compared) is marked for removal
                if (indexs[i] == 0) {
                    continue;
                }

                // Get the coordinates of both boxes
                float x1 = curMaxConf.get(0);
                float y1 = curMaxConf.get(1);
                float x2 = curMaxConf.get(2);
                float y2 = curMaxConf.get(3);
                float x3 = boxes.get(i).get(0);
                float y3 = boxes.get(i).get(1);
                float x4 = boxes.get(i).get(2);
                float y4 = boxes.get(i).get(3);

                // Skip if the boxes do not overlap
                if (x1 > x4 || x2 < x3 || y1 > y4 || y2 < y3) {
                    continue;
                }

                // Calculate the intersection area between the boxes
                float intersectionWidth = Math.max(x1, x3) - Math.min(x2, x4);
                float intersectionHeight = Math.max(y1, y3) - Math.min(y2, y4);
                float intersectionArea = Math.max(0, intersectionWidth * intersectionHeight);

                // Calculate the union area of the boxes
                float unionArea = (x2 - x1) * (y2 - y1) + (x4 - x3) * (y4 - y3) - intersectionArea;

                // Calculate the Intersection over Union (IoU)
                float iou = intersectionArea / unionArea;

                // Mark the box for removal if its IoU is above the threshold
                if (iou > model.nmsThreshold) {
                    if (boxes.get(i).get(4) <= curMaxConf.get(4)) {
                        indexs[i] = 0;
                    } else {
                        indexs[cur] = 0;
                    }
                }
            }
        }

        // Collect the boxes marked for keeping
        List<ArrayList<Float>> resBoxes = new LinkedList<>();
        for (int index = 0; index < indexs.length; index++) {
            if (indexs[index] == 1) {
                resBoxes.add(boxes.get(index));
            }
        }
        return resBoxes;
    }

}
