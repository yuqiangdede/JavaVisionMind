package com.yuqiangdede.reid.util;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import com.yuqiangdede.common.util.RandomProjectionUtils;
import com.yuqiangdede.reid.output.Feature;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.nio.FloatBuffer;
import java.util.Collections;

import static com.yuqiangdede.reid.config.ReidConstant.ONNX_PATH;

public class ReidUtil {
    private static final int W = 128, H = 256;
    static OrtEnvironment env;
    static OrtSession sess;

    static {
        try {
            env = OrtEnvironment.getEnvironment();
            sess = env.createSession(ONNX_PATH, new OrtSession.SessionOptions());
        } catch (OrtException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 使用单个图像生成特征嵌入。因为直接生成的是2048维的embedding，为了降低计算和存储成本（lucene仅支持1024维），使用随机投影将其降维到1024维。
     * 后面更换向量数据库，可以不在降维
     * @param img 输入的图像，类型为Mat。
     * @return Embedding类型，包含生成的特征嵌入。
     * @throws OrtException 当使用ONNX Runtime进行推理时，如果发生错误，则抛出此异常。
     */
    public static Feature featureSingle(Mat img) throws OrtException {
        float[] f = getFeatWithFlip(env, sess, img);
        
        return new Feature(null, RandomProjectionUtils.transform(f));
    }

    /**
     * 获取图像特征，并对其进行翻转处理。
     *
     * @param env  Ort环境实例。
     * @param sess Ort会话实例。
     * @param img  输入的图像。
     * @return 返回经过平均和L2范数处理后的图像特征数组。
     * @throws OrtException 如果Ort操作失败，将抛出此异常。
     */
    private static float[] getFeatWithFlip(OrtEnvironment env, OrtSession sess, Mat img) throws OrtException {
        float[] f1 = forwardOnce(env, sess, img);
        Mat flipped = new Mat();
        Core.flip(img, flipped, 1);
        float[] f2 = forwardOnce(env, sess, flipped);

        float[] avg = new float[f1.length];
        for (int i = 0; i < f1.length; i++) avg[i] = (f1[i] + f2[i]) * 0.5f;
        return l2norm(avg);
    }

    /**
     * 计算给定向量的L2范数，并返回归一化后的向量。
     *
     * @param v 输入的浮点数数组，表示待归一化的向量
     * @return 归一化后的向量，即原向量除以L2范数
     */
    private static float[] l2norm(float[] v) {
        double sum = 0;
        for (float f : v) sum += f * f;
        float inv = (float) (1.0 / Math.sqrt(sum + 1e-12));
        for (int i = 0; i < v.length; i++) v[i] *= inv;
        return v;
    }

    /**
     * 在单次前向传播中，通过给定的ONNX会话和图像提取特征。
     *
     * @param env  ONNX环境，用于创建张量等
     * @param sess ONNX会话，用于执行模型
     * @param img  输入图像，类型为Mat
     * @return 提取的特征，类型为float数组
     * @throws OrtException ONNX运行异常
     */
    private static float[] forwardOnce(OrtEnvironment env, OrtSession sess, Mat img) throws OrtException {
        float[] chw = preprocess(img);
        OnnxTensor tensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(chw),
                new long[]{1, 3, H, W});
        float[] feat = extractFeature(sess, tensor);
        tensor.close();
        return feat;
    }

    /**
     * 从给定的OrtSession中提取特征。
     *
     * @param session 一个有效的OrtSession对象。
     * @param input   一个OnnxTensor对象，作为模型的输入。
     * @return 提取的特征数组。
     * @throws OrtException 如果OrtSession在执行时发生错误。
     */
    private static float[] extractFeature(OrtSession session, OnnxTensor input) throws OrtException {
        OrtSession.Result res = session.run(Collections.singletonMap("input", input));
        float[] feat = ((float[][]) res.get(0).getValue())[0];
        res.close();
        return feat;
    }

    /**
     * 对输入的图片进行预处理，并返回预处理后的数据。
     *
     * @param bgr 输入的图片，类型为Mat，格式为BGR
     * @return 预处理后的数据，类型为float数组，长度为3*H*W
     */
    private static float[] preprocess(Mat bgr) {
        Mat resized = new Mat();
        Imgproc.resize(bgr, resized, new Size(W, H));

        resized.convertTo(resized, CvType.CV_32FC3, 1.0 / 255);

        // HWC → CHW 手动拷贝
        float[] chw = new float[3 * H * W];
        float[] mean = {0.485f, 0.456f, 0.406f};
        float[] std = {0.229f, 0.224f, 0.225f};

        int idx = 0;
        for (int c = 0; c < 3; c++) {
            for (int y = 0; y < H; y++) {
                for (int x = 0; x < W; x++) {
                    double[] pix = resized.get(y, x); // BGR
                    float val = (float) pix[2 - c];   // 转 RGB 并取单通道
                    chw[idx++] = (val - mean[c]) / std[c];
                }
            }
        }
        return chw;  // 长度 3*H*W
    }
}
