//package com.yuqiangdede.yolo.util.yolo;
//
//import ai.onnxruntime.*;
//import org.opencv.core.*;
//import org.opencv.imgcodecs.Imgcodecs;
//import org.opencv.imgproc.Imgproc;
//import org.opencv.dnn.Dnn;
//import ai.onnxruntime.TensorInfo;
//
//import java.nio.FloatBuffer;
//import java.nio.file.Files;
//import java.nio.file.Paths;
//import java.util.*;
//
///**
// * YOLO-OBB Java DEMO —— 去掉 LetterboxResult / record 用法，保持与 YoloSegJavaDemo1.java 的风格一致
// */
//public class YoloObbJavaDemo1 {
//    /* ----------- 参数 ----------- */
//    private static final int INPUT_W = 1024;
//    private static final int INPUT_H = 1024;
//    private static final float CONF_THR = 0.05f;
//    private static final float NMS_THR = 0.45f;
//
//    private static final String DLL_PATH = "../resource/lib/opencv/opencv_java490.dll";
//    private static final String MODEL_PATH = "../linux/resource/dsmsds-yolo/model/yolo-obb.onnx";
//    private static final String IMAGE_PATH = "F:/PyCode/yolov8/99999.jpg";
//
//    private static final String[] LABELS;
//
//    static {
//        /* JNI */
//        System.load(DLL_PATH);
//        /* 标签文件 */
//        String[] tmp;
//        try {
//            tmp = Files.readAllLines(Paths.get("coco.names")).toArray(new String[0]);
//        } catch (Exception e) {
//            tmp = new String[]{"obj"};
//        }
//        LABELS = tmp;
//    }
//
//    public static void main(String[] args) throws Exception {
//        /* 1. 读取原图 */
//        Mat orig = Imgcodecs.imread(IMAGE_PATH);
//        if (orig.empty()) {
//            System.err.println("无法读取图片");
//            return;
//        }
//
//        /* 2. letter-box, 获取比例与边距 */
//        int[] pad = new int[2];          // dx, dy
//        float[] ratioArr = new float[1]; // ratio
//        Mat padded = letterbox(orig, INPUT_W, INPUT_H, pad, ratioArr);
//        float ratio = ratioArr[0];
//        int dx = pad[0], dy = pad[1];
//
//        /* 3. 前处理 blob */
//        Mat blob = blobFromImage(padded);
//        float[] chw = matToCHW(blob);
//
//        /* 4. ONNX 推理 */
//        try (OrtEnvironment env = OrtEnvironment.getEnvironment();
//             OrtSession.SessionOptions so = new OrtSession.SessionOptions();
//             OrtSession session = env.createSession(MODEL_PATH, so);
//             OnnxTensor input = OnnxTensor.createTensor(env, FloatBuffer.wrap(chw), new long[]{1, 3, INPUT_H, INPUT_W})) {
//
//            Map<String, OnnxTensor> inp = Collections.singletonMap(session.getInputNames().iterator().next(), input);
//            OrtSession.Result out = session.run(inp);
//
//            TensorInfo ti = (TensorInfo) out.get(0).getInfo();
//            System.out.println("Output dims=" + Arrays.toString(ti.getShape()));
//
//
//            // 获取输出张量，形状一般为 [1, N, 6+nc]
//// 模型输出 dims=[1, 20, 21504] => (batch, 6+nc, N)
//            float[][][] raw = (float[][][]) out.get(0).getValue(); // [1][20][21504]
//            int feat = raw[0].length;           // 6+nc = 20
//            int N    = raw[0][0].length;        // 21504
//            float[][] preds = new float[N][feat];       // [N, 20]
//            for (int f = 0; f < feat; f++)
//                for (int n = 0; n < N; n++)
//                    preds[n][f] = raw[0][f][n];
//
//            float maxObj = 0;
//            for (float[] p : preds) maxObj = Math.max(maxObj, p[5]);
//            System.out.println("max obj = " + maxObj);
//
//            List<Detection> dets = decode(preds, ratio, dx, dy);
//            List<Detection> keep = nmsRotated(dets);
//
//            /* 5. 可视化 */
//            keep.forEach(d->drawRotated(orig, d));
//            Imgcodecs.imwrite("result_obb.jpg", orig);
//            System.out.println("✓ 推理完成，结果保存至 result_obb.jpg");
//
//            System.out.println("decoded boxes = " + dets.size());
//            System.out.println("after NMS     = " + keep.size());
//        }
//    }
//
//    /* ---------- Letter-box 缩放 ---------- */
//    private static Mat letterbox(Mat src, int newW, int newH, int[] pad, float[] ratioArr) {
//        double r = Math.min(newW / (double) src.width(), newH / (double) src.height());
//        int rw = (int) Math.round(src.width() * r);
//        int rh = (int) Math.round(src.height() * r);
//        int dx = (newW - rw) / 2;
//        int dy = (newH - rh) / 2;
//
//        Mat resized = new Mat();
//        Imgproc.resize(src, resized, new Size(rw, rh));
//        Mat padded = new Mat(new Size(newW, newH), CvType.CV_8UC3, new Scalar(114, 114, 114));
//        resized.copyTo(padded.submat(dy, dy + rh, dx, dx + rw));
//
//        pad[0] = dx;
//        pad[1] = dy;
//        ratioArr[0] = (float) r;
//        return padded;
//    }
//
//    /* ---------- 前处理 ---------- */
//    private static Mat blobFromImage(Mat rgb) {
//        Mat f = new Mat();
//        Imgproc.cvtColor(rgb, rgb, Imgproc.COLOR_BGR2RGB);
//        rgb.convertTo(f, CvType.CV_32F, 1.0 / 255);
//        return f;
//    }
//
//    private static float[] matToCHW(Mat m) {
//        int h = m.rows(), w = m.cols(), c = m.channels();
//        float[] hwc = new float[h * w * c];
//        m.get(0, 0, hwc);
//        float[] chw = new float[c * h * w];
//        for (int ch = 0; ch < c; ch++)
//            for (int y = 0; y < h; y++)
//                for (int x = 0; x < w; x++)
//                    chw[ch * h * w + y * w + x] = hwc[(y * w + x) * c + ch];
//        return chw;
//    }
//
//    /* ---------- 后处理 ---------- */
//    private static List<Detection> decode(float[][] preds, float r, int dx, int dy) {
//        int nc = preds[0].length - 6;
//        List<Detection> res = new ArrayList<>();
//        for (float[] p : preds) {
//            float obj = p[5];
//            if (obj < CONF_THR) continue;
//            int cls = 0;
//            float clsScore = 0;
//            for (int i = 0; i < nc; i++)
//                if (p[6 + i] > clsScore) {
//                    clsScore = p[6 + i];
//                    cls = i;
//                }
//            float score = obj * clsScore;
//            if (score < CONF_THR) continue;
//            float cx = (p[0] - dx) / r, cy = (p[1] - dy) / r, w = p[2] / r, h = p[3] / r, th = p[4];
//            res.add(new Detection(cx, cy, w, h, th, score, LABELS[Math.min(cls, LABELS.length - 1)]));
//        }
//        return res;
//    }
//
//    private static List<Detection> nmsRotated(List<Detection> dets) {
//        if (dets.isEmpty()) return dets;
//        RotatedRect[] rects = new RotatedRect[dets.size()];
//        float[] scores = new float[dets.size()];
//        for (int i = 0; i < dets.size(); i++) {
//            rects[i] = dets.get(i).toRect();
//            scores[i] = (float) dets.get(i).score;
//        }
//        MatOfRotatedRect rr = new MatOfRotatedRect(rects);
//        MatOfFloat sc = new MatOfFloat(scores);
//        MatOfInt keep = new MatOfInt();
//        Dnn.NMSBoxesRotated(rr, sc, 0.0f, NMS_THR, keep);
//        List<Detection> out = new ArrayList<>();
//        for (int idx : keep.toArray()) out.add(dets.get(idx));
//        return out;
//    }
//
//    private static void drawRotated(Mat img, Detection d) {
//        RotatedRect r = d.toRect();
//        Point[] pts = new Point[4];
//        r.points(pts);
//        for (int i = 0; i < 4; i++) Imgproc.line(img, pts[i], pts[(i + 1) % 4], new Scalar(0, 255, 0), 2);
//        Imgproc.putText(img, d.label + String.format(" %.2f", d.score), pts[1], Imgproc.FONT_HERSHEY_SIMPLEX, 0.6, new Scalar(0, 255, 0), 2);
//    }
//
//    /* ---------- 数据结构 ---------- */
//    private static class Detection {
//        float cx, cy, w, h, theta, score;
//        String label;
//
//        Detection(float cx, float cy, float w, float h, float t, float s, String l) {
//            this.cx = cx;
//            this.cy = cy;
//            this.w = w;
//            this.h = h;
//            this.theta = t;
//            this.score = s;
//            this.label = l;
//        }
//
//        RotatedRect toRect() {
//            return new RotatedRect(new Point(cx, cy), new Size(w, h), Math.toDegrees(theta));
//        }
//    }
//}
