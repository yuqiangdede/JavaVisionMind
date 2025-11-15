package com.yuqiangdede.yolo.util.yolo;

import ai.onnxruntime.*;
import com.yuqiangdede.yolo.config.Constant;
import lombok.extern.slf4j.Slf4j;
import org.opencv.core.*;

import java.util.*;


/**
 * 结构化分析的具体逻辑，目前的逻辑都是针对yoloV8系列的处理
 * YoloDetectionResult detection = YoloV8Util.detect(path, model);
 */
@Slf4j
public class YoloV11Util extends YoloUtil {

    static final Model yolomodel;
    static final Model yoloFaceModel;
    static final Model yoloLpModel;

    static {
        try {
            yolomodel = load(Constant.YOLO_ONNX_PATH);
            yoloFaceModel = load(Constant.YOLO_FACE_ONNX_PATH);
            yoloLpModel = load(Constant.YOLO_LP_ONNX_PATH);
        } catch (OrtException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 对图片根据模型来进行预测，分为下面几步
     * 1、图片预处理
     * 主要就是一个resize的操作 和 Tensor数据格式的转换
     * 2、进行预测 model.session.run
     * 3、返回结果的后续处理
     * 拿到返回的数据，一个矩阵（两维数组） dataRes[0]
     * 将这个矩阵转置，转换成坐标xy 宽高wh 置信度c 类型t 长度为6的数据
     * *模型的预测结果首先被获取并存储在一个三维浮点数数组dataRes中，其中dataRes[0]是我们关注的二维数组（矩阵）。
     * *这个二维数组包含了预测结果的中心坐标（xy）、宽高（wh）、置信度（c）和类型（t）信息。
     * *为了将这些信息转换成适合进一步处理的格式，我们需要进行矩阵转置操作。
     * *这个过程实际上是将原始的模型输出格式（通常是模型特定的，可能是CHW或其他格式）转换成一个更通用、更易于处理的格式（在这里是每个预测框一个行向量，包含位置、尺寸、置信度和类别信息）
     * 补充置信度 和 类型数据
     * 根据经模型中的 confThreshold 来过滤置信度太小的数据
     * 把xywh转xyxy（两个对角点来表示矩形），同时把检测的坐标框进行反resize的操作，得到实际的坐标位置
     * 非极大值抑制（NMS）用以去除多余的边缘响应或重复的检测框，保留最佳的那些框

     * 最终返回一个 YoloDetectionResult，包含预测框列表和类型名称映射

     *

     * @param src   带预测图片Mat

     * @param model 模型

     * @param conf  置信度

     * @return 封装后的检测结果

     */
    private static YoloDetectionResult predictor(Mat src, Model model, Float conf) {
        try (OnnxTensor tensor = transferTensor(src, model)) {
            // 预处理 转换成Tensor数据格式
            try (OrtSession.Result result = model.session.run(Collections.singletonMap("images", tensor))) {

                try (OnnxTensor res = (OnnxTensor) result.get(0)) {
                    // 将结果矩阵转置
                    // 拿到我们关心的结果数据
                    /*
                        推理给出来的未转置的数据格式示意（X Y W宽 H高 C置信度 T类型）
                              X1 X2 X3 ... Xn
                              Y1 Y2 Y3 ... Yn
                              W1 W2 W3 ... Wn
                              H1 H2 H3 ... Hn
                        T1    C1 C2 C3 ... Cn     --- 相对于类型1，在目标框XYWH下的置信度
                        T2    C1 C2 C3 ... Cn
                        .....................
                        Tm    C1 C2 C3 ... Cm

                        我们需要把其转换成 每个目标框一行，取最大置信度的值 和 对应的类型数据
                        目标1 X1 Y1 W1 H1 MAX(C) T
                        目标2 X2 Y2 W2 H2 MAX(C) T
                        目标3 X3 Y3 W3 H3 MAX(C) T
                        .........................
                        目标n Xn Yn Wn Hn MAX(C) T
                     */
                    float[][] data = ((float[][][]) res.getValue())[0];

                    // 这里的每行长度6 代表 原点（中心点） + 宽高 + 置信度 + 类型编码，先填充前四个值，将xywh的这一部分部分转置填入
                    Float[][] transpositionData = new Float[data[0].length][6];
                    for (int i = 0; i < 4; i++) {
                        for (int j = 0; j < data[0].length; j++) {
                            transpositionData[j][i] = data[i][j];
                        }
                    }
                    // 保存每个检查框置信值最高的类型置信值和该类型下标，数组的第5位置信度 和 第6位类型
                    for (int i = 0; i < data[0].length; i++) {
                        for (int j = 4; j < data.length; j++) {
                            // 如果设置进去的置信度 比 原始数据中的小，就拿原始数据来替换，同时类型也替换掉
                            if (transpositionData[i][4] == null
                                    || transpositionData[i][4] < data[j][i]) {
                                transpositionData[i][4] = data[j][i];//置信值
                                transpositionData[i][5] = (float) (j - 4); //类型编号
                            }
                        }
                    }

                    List<ArrayList<Float>> boxes = new ArrayList<>();

                    // 因为模型预测的图片是经过resize的图片，给出来的坐标都是相对于resize的坐标，所以最终坐标 还是要还原回来的
                    // 因为预处理的时候按照长边画的正方形，所以这里用MAX
                    float scaleW = (float) Math.max(src.width(), src.height()) / model.netWidth;
                    float scaleH = (float) Math.max(src.width(), src.height()) / model.netHeight;
                    // 置信值过滤,xywh转xyxy,还原坐标的resize
                    for (Float[] d : transpositionData) {
                        // 置信值过滤
                        if (d[4] > (conf == null ? model.confThreshold : conf)) {
                            // xywh(xy为中心点)转xyxy
                            d[0] = d[0] - d[2] / 2;
                            d[1] = d[1] - d[3] / 2;
                            d[2] = d[0] + d[2];
                            d[3] = d[1] + d[3];
                            // 还原坐标的resize，得到原始坐标
                            d[0] = d[0] * scaleW;
                            d[1] = d[1] * scaleH;
                            d[2] = d[2] * scaleW;
                            d[3] = d[3] * scaleH;
                            ArrayList<Float> box = new ArrayList<>(Arrays.asList(d));
                            boxes.add(box);
                        }
                    }
                    // 非极大值抑制 NMS 用以去除多余的边缘响应或重复的检测框，保留最佳的那些框，内层长度为6
                    List<List<Float>> boxesAfterNMS = NMS(model, boxes).stream()
                            .map(List::copyOf)
                            .toList();
                    return new YoloDetectionResult(boxesAfterNMS, Map.copyOf(model.names));
                }
            }
        } catch (OrtException e) {
            log.error("detect error", e);
            throw new RuntimeException(e);
        }
    }

    /**
     * 用来指定使用的模型，也可以直接调用predictor(Mat src, Model model) 但是需要自己加载模型
     *
     * @param mat mat
     * @return 封装后的检测结果

     */
    public static YoloDetectionResult predictor(Mat mat, Float conf) {
        return predictor(mat, yolomodel, conf);
    }

    public static YoloDetectionResult predictorFace(Mat mat, Float conf) {
        return predictor(mat, yoloFaceModel, conf);
    }

    public static YoloDetectionResult predictorLicensePlate(Mat mat, Float conf) {
        return predictor(mat, yoloLpModel, conf);
    }

    /**
     * 非极大值抑制（NMS），用于去除多余的边缘响应或重复的检测框，保留最佳的检测框。
     * 目标识别专用
     *
     * @param model 模型对象
     * @param boxes 检测框列表，每个检测框由一组浮点数表示
     * @return 封装后的检测结果

     */
    private static List<ArrayList<Float>> NMS(Model model, List<ArrayList<Float>> boxes) {
        int[] indexs = new int[boxes.size()];
        Arrays.fill(indexs, 1); // 用于标记1保留，0删除

        // 遍历每个框
        for (int cur = 0; cur < boxes.size(); cur++) {
            // 如果当前框已被标记为删除，则跳过
            if (indexs[cur] == 0) {
                continue;
            }
            ArrayList<Float> curMaxConf = boxes.get(cur); // 当前框代表该类置信值最大的框

            // 遍历后面的框
            for (int i = cur + 1; i < boxes.size(); i++) {
                // 如果当前框已被标记为删除，则跳过
                if (indexs[i] == 0) {
                    continue;
                }
                float classIndex = boxes.get(i).get(5);

                // 两个检测框都检测到同一类数据，通过iou来判断是否检测到同一目标，这就是非极大值抑制
                if (classIndex == curMaxConf.get(5)) {
                    // 获取两个框的坐标
                    float x1 = curMaxConf.get(0);
                    float y1 = curMaxConf.get(1);
                    float x2 = curMaxConf.get(2);
                    float y2 = curMaxConf.get(3);
                    float x3 = boxes.get(i).get(0);
                    float y3 = boxes.get(i).get(1);
                    float x4 = boxes.get(i).get(2);
                    float y4 = boxes.get(i).get(3);

                    // 将几种不相交的情况排除
                    // 提示:x1y1、x2y2、x3y3、x4y4对应两框的左上角和右下角
                    if (x1 > x4 || x2 < x3 || y1 > y4 || y2 < y3) {
                        continue;
                    }

                    // 计算两个矩形的交集面积
                    float intersectionWidth = Math.max(x1, x3) - Math.min(x2, x4);
                    float intersectionHeight = Math.max(y1, y3) - Math.min(y2, y4);
                    float intersectionArea = Math.max(0, intersectionWidth * intersectionHeight);

                    // 计算两个矩形的并集面积
                    float unionArea = (x2 - x1) * (y2 - y1) + (x4 - x3) * (y4 - y3) - intersectionArea;

                    // 计算IoU
                    float iou = intersectionArea / unionArea;

                    // 对交并比超过阈值的标记,如果超过阈值，就保留置信度较高的那一个框
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

        // 收集标记为保留的框
        List<ArrayList<Float>> resBoxes = new LinkedList<>();
        for (int index = 0; index < indexs.length; index++) {
            if (indexs[index] == 1) {
                resBoxes.add(boxes.get(index));
            }
        }

        return resBoxes;
    }

    /**
     * 获取模型的class字典数据
     *
     * @return 封装后的检测结果

     */
    public static Map<Integer, String> getTypeName() {
        return yolomodel.names;
    }
}


 
