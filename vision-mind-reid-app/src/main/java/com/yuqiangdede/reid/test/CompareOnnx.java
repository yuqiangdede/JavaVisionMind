package com.yuqiangdede.reid.test;

import ai.onnxruntime.*;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.opencv.core.Mat;
import org.opencv.core.Size;

import java.nio.FloatBuffer;
import java.util.Collections;

public class CompareOnnx {
    // ---------------- 配置 ----------------
    private static final String ONNX_PATH = "..\\resource\\dsmsds-reid\\model\\AGW_R50-ibn-bn.onnx";
    private static final String[] IMGS = {
            "..\\Persons\\1.jpg",
            "..\\Persons\\2.jpg",
            "..\\Persons\\3.jpg"
    };
    private static final int W = 128, H = 256;

    static { System.load("..\\resource\\lib\\opencv\\opencv_java490.dll"); }

    // ------------ 预处理：resize + BGR→RGB + 标准化 ------------
    private static float[] preprocess(Mat bgr) {
        Mat resized = new Mat();
        Imgproc.resize(bgr, resized, new Size(W, H));

        resized.convertTo(resized, CvType.CV_32FC3, 1.0 / 255);

        // HWC → CHW 手动拷贝
        float[] chw = new float[3 * H * W];
        float[] mean = {0.485f, 0.456f, 0.406f};
        float[] std  = {0.229f, 0.224f, 0.225f};

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

    // ------------ 推理：原图 + 翻转 → 平均 → L2 ------------
    private static float[] extractFeature(OrtSession session, OnnxTensor input) throws OrtException {
        OrtSession.Result res = session.run(Collections.singletonMap("input", input));
        float[] feat = ((float[][]) res.get(0).getValue())[0];
        res.close();
        return feat;
    }

    private static float[] l2norm(float[] v) {
        double sum = 0;
        for (float f : v) sum += f * f;
        float inv = (float) (1.0 / Math.sqrt(sum + 1e-12));
        for (int i = 0; i < v.length; i++) v[i] *= inv;
        return v;
    }

    private static float[] getFeatWithFlip(OrtEnvironment env, OrtSession sess, Mat img) throws OrtException {
        float[] f1 = forwardOnce(env, sess, img);
        Mat flipped = new Mat();
        Core.flip(img, flipped, 1);
        float[] f2 = forwardOnce(env, sess, flipped);

        float[] avg = new float[f1.length];
        for (int i = 0; i < f1.length; i++) avg[i] = (f1[i] + f2[i]) * 0.5f;
        return l2norm(avg);
    }

    private static float[] forwardOnce(OrtEnvironment env, OrtSession sess, Mat img) throws OrtException {
        float[] chw = preprocess(img);
        OnnxTensor tensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(chw),
                new long[]{1, 3, H, W});
        float[] feat = extractFeature(sess, tensor);
        tensor.close();
        return feat;
    }

    // ------------ 余弦相似度 ------------
    private static float cosine(float[] a, float[] b) {
        float dot = 0;
        for (int i = 0; i < a.length; i++) dot += a[i] * b[i];
        return dot;  // 已 L2，无需除范数
    }

    // ---------------- 主函数 ----------------
    public static void main(String[] args) throws Exception {
        OrtEnvironment env = OrtEnvironment.getEnvironment();
        OrtSession sess = env.createSession(ONNX_PATH, new OrtSession.SessionOptions());

        float[][] feats = new float[IMGS.length][];

        for (int i = 0; i < IMGS.length; i++) {
            Mat img = Imgcodecs.imread(IMGS[i]);          // BGR
            long begin = System.currentTimeMillis();
            feats[i] = getFeatWithFlip(env, sess, img);
            System.out.printf(System.currentTimeMillis() - begin + " ms%n");
        }

        float cos56  = cosine(feats[0], feats[1]);
        float cos517 = cosine(feats[0], feats[2]);

        System.out.printf("%nCosine(5 vs 6)  : %.9f%n", cos56);
        System.out.printf("Cosine(5 vs 17) : %.9f%n", cos517);
        System.out.printf("差值             : %.9f%n", cos56 - cos517);

        sess.close();
        env.close();
    }
}
