package com.yuqiangdede.yolo.util.yolo;

import ai.onnxruntime.*;
import com.yuqiangdede.yolo.config.Constant;
import lombok.extern.slf4j.Slf4j;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Rect;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.nio.FloatBuffer;
import java.util.*;


@Slf4j
public class YoloBaseUtil {

    private static final OrtEnvironment environment;

    static {
        if (Constant.USE_GPU) {
            // 加载GPU版本的ONNX Runtime
            environment = OrtEnvironment.getEnvironment(OrtLoggingLevel.ORT_LOGGING_LEVEL_WARNING, "GPUEnvironment");
            log.info("use onnxruntime_gpu");
        } else {
            environment = OrtEnvironment.getEnvironment();
            log.info("use onnxruntime");
        }
    }

    /**
     * 将字符串转换为Map对象
     *
     * @param input 字符串类型的输入
     * @return Map类型的键值对
     */
    private static Map<Integer, String> stringToMap(String input) {
        Map<Integer, String> map = new HashMap<>();
        // 去除首尾的大括号
        input = input.substring(1, input.length() - 1);
        // 按逗号分割
        String[] entries = input.split(",");
        for (String entry : entries) {
            // 按冒号分割键和值
            String[] keyValue = entry.split(":");
            Integer key = Integer.valueOf(keyValue[0].trim());
            String value = keyValue[1].trim();
            map.put(key, value);
        }
        return map;
    }

    /**
     * 加载模型,可以指定其他的模型，默认不使用，直接用默认带的模型就可以
     *
     * @param path 模型路径
     * @return 模型实体
     */
    static Model load(String path, boolean nmsEnabled) throws OrtException {
        try (OrtSession.SessionOptions options = new OrtSession.SessionOptions()) {
            OrtSession session = environment.createSession(path, options);
            OnnxModelMetadata metadata = session.getMetadata();
            Map<String, NodeInfo> infoMap = session.getInputInfo();
            TensorInfo nodeInfo = (TensorInfo) infoMap.get("images").getInfo();
            String nameClass = metadata.getCustomMetadata().get("names");

            Map<Integer, String> names = stringToMap(nameClass);
            long count = 1;
            long channels = nodeInfo.getShape()[1];
            long netHeight = nodeInfo.getShape()[2];
            long netWidth = nodeInfo.getShape()[3];
            if (netHeight <= 0 || netWidth <= 0) {
                log.warn("Invalid model input size detected. Falling back to 640x640. netHeight={}, netWidth={}", netHeight, netWidth);
                netHeight = 640;
                netWidth = 640;
            }
            float confThreshold = Constant.CONF_THRESHOLD;
            float nmsThreshold = Constant.NMS_THRESHOLD;

            log.info("load yolo model: path={}, nmsEnabled={}", path, nmsEnabled);
            return new Model(environment, session, names, count, channels, netHeight, netWidth, confThreshold, nmsThreshold, nmsEnabled);
        }
    }



    static class Model {
        public OrtEnvironment env;
        public OrtSession session;
        public Map<Integer, String> names;
        public long count;
        public long channels;
        public long netHeight;
        public long netWidth;
        /**
         * 置信度，小于这个置信度的直接给过滤掉，不能配置的太小，不然结果会太多。应用层要是需要更准确的数据，拿到结果之后自己再过滤就行
         */
        public float confThreshold;
        /**
         * nms算法的阈值，两个框相交面积，大于这个阈值的就删除掉
         */
        public float nmsThreshold;
        public boolean nmsEnabled;

        public Model(OrtEnvironment env, OrtSession session, Map<Integer, String> names, long count, long channels, long netHeight, long netWidth, float confThreshold, float nmsThreshold, boolean nmsEnabled) {
            this.env = env;
            this.session = session;
            this.names = names;
            this.count = count;
            this.channels = channels;
            this.netHeight = netHeight;
            this.netWidth = netWidth;
            this.confThreshold = confThreshold;
            this.nmsThreshold = nmsThreshold;
            this.nmsEnabled = nmsEnabled;
        }



    }

    /**
     * 转换成Tensor数据格式
     *
     * @param mat   原始图片
     * @param model 模型
     * @return OnnxTensor
     */
    static OnnxTensor transferTensor(Mat mat, Model model) throws OrtException {

        // 计算最大边长并创建空白图像,这里是先按照长边造一个正方形出来，然后把图片放到左上角，后面再做缩放，防止图像宽高形变
        int maxImageLength = Math.max(mat.cols(), mat.rows());
        Mat maxImage = Mat.zeros(new Size(maxImageLength, maxImageLength), CvType.CV_8UC3);
        Rect roi = new Rect(0, 0, mat.cols(), mat.rows());
        mat.copyTo(new Mat(maxImage, roi));

        Mat dst = new Mat();
        // 转换成模型的大小
        Imgproc.resize(maxImage, dst, new Size(model.netWidth, model.netHeight));
        // 将图片颜色空间从BGR转换为RGB，因为模型训练时使用的图片颜色空间是RGB
        Imgproc.cvtColor(dst, dst, Imgproc.COLOR_BGR2RGB);
        // 将图片数据类型转换为32位浮点数，并将每个像素值归一化到0-1之间
        dst.convertTo(dst, CvType.CV_32FC3, 1. / 255);
        // 创建一个数组用于存储调整后的图片数据，大小为通道数*图片宽度*图片高度
        float[] whc = new float[Long.valueOf(model.channels).intValue() * Long.valueOf(model.netWidth).intValue() * Long.valueOf(model.netHeight).intValue()];
        // 从Mat对象中获取图片数据并存储到数组中
        dst.get(0, 0, whc);
        // 将宽高通道（WHC）格式的数据转换为通道宽高（CHW）格式，因为模型输入需要这种格式
        float[] chw = whc2cwh(whc);
        // 使用调整后的图片数据创建一个OnnxTensor对象，这将作为模型的输入
        return OnnxTensor.createTensor(model.env, FloatBuffer.wrap(chw), new long[]{model.count, model.channels, model.netWidth, model.netHeight});
    }

    /**
     * 将宽高类型转换为类型宽高
     *
     * @param src 宽高类型的图片数据
     * @return 类型宽高的图片数据
     */
    private static float[] whc2cwh(float[] src) {
        // 创建一个与src相同长度的数组chw，用于存储转换后的类型宽高数据
        float[] chw = new float[src.length];
        // 初始化计数器j为0
        int j = 0;
        // 遍历每个通道
        for (int ch = 0; ch < 3; ++ch) {
            // 在每个通道内，从当前通道开始，每隔3个元素取一个元素
            for (int i = ch; i < src.length; i += 3) {
                // 将取出的元素赋值给chw数组的当前位置
                chw[j] = src[i];
                // 计数器j自增
                j++;
            }
        }
        // 返回转换后的类型宽高数据
        return chw;
    }

}
