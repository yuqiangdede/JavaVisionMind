package com.yuqiangdede.yolo.util.yolo;

import ai.onnxruntime.*;
import com.yuqiangdede.common.dto.output.Box;
import com.yuqiangdede.yolo.config.Constant;
import lombok.extern.slf4j.Slf4j;
import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;

import java.nio.FloatBuffer;
import java.util.*;

import static com.yuqiangdede.yolo.config.Constant.*;

@Slf4j
public class YoloFastSAMUtil {
    private static final OrtEnvironment env;

    static {
        env = OrtEnvironment.getEnvironment();
    }


    public static List<Box> predictor(Mat mat) throws OrtException {
        List<Box> result = new ArrayList<>();
        OrtSession.SessionOptions opts = new OrtSession.SessionOptions();
        try (OrtSession session = env.createSession(FAST_SAM_ONNX, opts)) {
            // 预处理图像
            PreprocessResult prep = preprocess(mat);
            try (OnnxTensor inputTensor = OnnxTensor.createTensor(
                    env,
                    FloatBuffer.wrap(Arrays.copyOf(prep.nchw, prep.nchw.length)),
                    new long[]{1, 3, SAM_SIZE, SAM_SIZE})) {

                // 创建输入张量
                Map<String, OnnxTensor> inputs = Collections.singletonMap(
                        session.getInputNames().iterator().next(), inputTensor);


                try (OrtSession.Result out = session.run(inputs)) {
                    // 获取预测结果
                    OnnxTensor predTensor = (OnnxTensor) out.get(0);
                    Object rawValue = predTensor.getValue();
                    // 检查输出张量类型
                    if (!(rawValue instanceof float[][][])) {
                        throw new OrtException("Unexpected output tensor type");
                    }
                    float[][][] predRaw = (float[][][]) rawValue;


                    // 后处理预测结果
                    DetectionResult det = postProcess(predRaw[0], prep);
                    // 将检测框和分数转换为Box对象并添加到结果列表中
                    for (float[] b : det.boxes) {
                        int index = det.boxes.indexOf(b);
                        Box box = new Box(b[0], b[1], b[2], b[3], det.scores.get(index));
                        result.add(box);
                    }
                }
            }
        }
        return result;
    }


    /**
     * 对预测结果进行后处理。
     *
     * @param pred37x8400 预测结果，是一个二维浮点数组，其中第一维表示预测的锚点数量，第二维表示每个锚点的预测值。
     * @param prep        预处理结果，包含图像的缩放比例和偏移量等信息。
     * @return 返回一个DetectionResult对象，该对象包含了最终的检测框、分数和系数。
     */
    private static DetectionResult postProcess(float[][] pred37x8400,
                                               PreprocessResult prep) {

        int numAnchors = pred37x8400[0].length; // 获取锚点的数量
        List<float[]> boxesXYWH = new ArrayList<>(); // 存储转换后的边界框（xywh格式）
        List<Float> scores = new ArrayList<>(); // 存储每个锚点的得分

        for (int i = 0; i < numAnchors; i++) {
            float score = pred37x8400[4][i]; // 获取当前锚点的得分
            if (score > Constant.SAM_CONF) { // 如果得分大于阈值
                boxesXYWH.add(new float[]{
                        pred37x8400[0][i], pred37x8400[1][i],
                        pred37x8400[2][i], pred37x8400[3][i]}); // 将xywh格式的边界框添加到列表中
                scores.add(score); // 将得分添加到列表中

            }
        }

        // xywh → xyxy, scale back to original image
        List<float[]> boxesXYXY = new ArrayList<>(); // 存储转换后的边界框（xyxy格式）
        for (float[] b : boxesXYWH) {
            float cx = b[0], cy = b[1], w = b[2], h = b[3]; // 提取中心点坐标和宽高
            float x1 = clamp((cx - w / 2 - prep.dw) / prep.r, 0f, prep.origW); // 计算左上角x坐标并裁剪
            float y1 = clamp((cy - h / 2 - prep.dh) / prep.r, 0f, prep.origH); // 计算左上角y坐标并裁剪
            float x2 = clamp((cx + w / 2 - prep.dw) / prep.r, 0f, prep.origW); // 计算右下角x坐标并裁剪
            float y2 = clamp((cy + h / 2 - prep.dh) / prep.r, 0f, prep.origH); // 计算右下角y坐标并裁剪
            boxesXYXY.add(new float[]{x1, y1, x2, y2}); // 将xyxy格式的边界框添加到列表中
        }

        // NMS
        List<Integer> keep = nms(boxesXYXY, scores); // 执行非极大值抑制，获取保留的索引

        // gather kept results
        List<float[]> finalBoxes = new ArrayList<>(); // 存储最终的边界框
        List<Float> finalScores = new ArrayList<>(); // 存储最终的得分
        for (int idx : keep) {
            finalBoxes.add(boxesXYXY.get(idx)); // 添加保留的边界框
            finalScores.add(scores.get(idx)); // 添加保留的得分
        }
        return new DetectionResult(finalBoxes, finalScores); // 返回检测结果对象
    }

    /**
     * 非极大值抑制（Non-Maximum Suppression, NMS）算法实现
     *
     * @param boxes  一个包含所有检测框的列表，每个检测框由一个包含四个元素的浮点数数组表示，分别代表检测框的左上角x坐标、左上角y坐标、右下角x坐标、右下角y坐标
     * @param scores 一个包含所有检测框对应置信度的列表，每个置信度为一个浮点数
     * @return 一个包含保留下来的检测框索引的列表
     */
    private static List<Integer> nms(List<float[]> boxes, List<Float> scores) {
        int n = boxes.size();
        Integer[] order = new Integer[n];
        // 初始化索引数组
        for (int i = 0; i < n; i++) order[i] = i;
        // 根据置信度对索引进行排序，置信度高的索引排在前面
        Arrays.sort(order, (a, b)->Float.compare(scores.get(b), scores.get(a)));

        List<Integer> keep = new ArrayList<>();
        boolean[] removed = new boolean[n];
        // 遍历排序后的索引数组
        for (int _i = 0; _i < n; _i++) {
            int i = order[_i];
            // 如果当前索引已经被移除，则跳过
            if (removed[i]) continue;
            // 将当前索引添加到保留列表中
            keep.add(i);
            // 遍历当前索引之后的索引数组
            for (int _j = _i + 1; _j < n; _j++) {
                int j = order[_j];
                // 如果当前索引之后的某个索引已经被移除，则跳过
                if (removed[j]) continue;
                // 计算当前索引和当前索引之后的某个索引之间的交并比
                if (iou(boxes.get(i), boxes.get(j)) > Constant.SAM_IOU)
                    // 如果交并比大于阈值，则将当前索引之后的某个索引标记为已移除
                    removed[j] = true;
            }
        }
        return keep;
    }


    /**
     * 计算两个矩形框的交并比（IoU, Intersection over Union）
     *
     * @param a 第一个矩形框的坐标数组，格式为[x1, y1, x2, y2]，其中(x1, y1)是矩形框左上角的坐标，(x2, y2)是矩形框右下角的坐标
     * @param b 第二个矩形框的坐标数组，格式为[x1, y1, x2, y2]，其中(x1, y1)是矩形框左上角的坐标，(x2, y2)是矩形框右下角的坐标
     * @return 两个矩形框的交并比（IoU）
     */
    private static float iou(float[] a, float[] b) {
        // 计算两个矩形框交集的左上角坐标
        float xx1 = Math.max(a[0], b[0]);
        float yy1 = Math.max(a[1], b[1]);
        // 计算两个矩形框交集的右下角坐标
        float xx2 = Math.min(a[2], b[2]);
        float yy2 = Math.min(a[3], b[3]);
        // 计算交集的宽度和高度
        float w = Math.max(0, xx2 - xx1);
        float h = Math.max(0, yy2 - yy1);
        // 计算交集面积
        float inter = w * h;
        // 计算矩形框a的面积
        float areaA = (a[2] - a[0]) * (a[3] - a[1]);
        // 计算矩形框b的面积
        float areaB = (b[2] - b[0]) * (b[3] - b[1]);
        // 计算交并比
        return inter / (areaA + areaB - inter + 1e-6f);
    }


    /**
     * 对输入的图像进行预处理。
     *
     * @param imgBGR 需要处理的图像，格式为Mat。
     * @return 返回一个PreprocessResult对象，包含预处理后的图像数据、原图宽高、缩放比例以及填充的宽度和高度。
     */
    private static PreprocessResult preprocess(Mat imgBGR) {
        // 获取输入图像的原始高度和宽度
        int h0 = imgBGR.rows(), w0 = imgBGR.cols();

        // 计算缩放比例
        float r = (float) SAM_SIZE / Math.max(h0, w0);
        // 计算缩放后的新宽度和新高度
        int newW = Math.round(w0 * r);
        int newH = Math.round(h0 * r);
        // 计算填充的宽度和高度
        int dw = (SAM_SIZE - newW) / 2;
        int dh = (SAM_SIZE - newH) / 2;

        // 创建新的Mat对象，用于存储缩放后的图像
        Mat resized = new Mat();
        // 对图像进行缩放
        Imgproc.resize(imgBGR, resized, new Size(newW, newH), 0, 0, Imgproc.INTER_LINEAR);

        // 创建一个新的Mat对象，用于存储填充后的图像，并初始化填充颜色为灰色
        Mat padded = new Mat(SAM_SIZE, SAM_SIZE, CvType.CV_8UC3,
                new Scalar(114, 114, 114));
        // 将缩放后的图像复制到填充后的图像中
        resized.copyTo(padded.submat(new Rect(dw, dh, newW, newH)));

        // 将填充后的图像颜色空间从BGR转换为RGB
        Imgproc.cvtColor(padded, padded, Imgproc.COLOR_BGR2RGB);

        // 创建一个浮点数数组，用于存储预处理后的图像数据
        float[] nchw = new float[3 * SAM_SIZE * SAM_SIZE];
        int idx = 0;
        // 遍历填充后的图像，将每个像素点的颜色值转换为浮点数并存储在nchw数组中
        for (int c = 0; c < 3; c++) {
            for (int y = 0; y < SAM_SIZE; y++) {
                for (int x = 0; x < SAM_SIZE; x++) {
                    nchw[idx++] = (float) (padded.get(y, x)[c] / 255.0);
                }
            }
        }
        // 返回预处理后的结果
        return new PreprocessResult(nchw, w0, h0, r, dw, dh);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }


    private static class PreprocessResult {
        final float[] nchw;
        final int origW, origH, dw, dh;
        final float r;

        PreprocessResult(float[] nchw, int origW, int origH,
                         float r, int dw, int dh) {
            this.nchw = nchw;
            this.origW = origW;
            this.origH = origH;
            this.r = r;
            this.dw = dw;
            this.dh = dh;
        }
    }

    private static class DetectionResult {
        final List<float[]> boxes;
        final List<Float> scores;

        DetectionResult(List<float[]> boxes, List<Float> scores) {
            this.boxes = boxes;
            this.scores = scores;
        }
    }
}
