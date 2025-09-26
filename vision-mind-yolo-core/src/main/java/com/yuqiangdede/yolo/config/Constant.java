package com.yuqiangdede.yolo.config;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

@Slf4j
public class Constant {
    public static final float SAM_CONF;
    public static final float SAM_IOU = 0.7f;
    public static final int SAM_SIZE = 640;

    // Native library locations
    public static final String OPENCV_DLL_PATH;
    public static final String OPENCV_SO_PATH;

    // YOLO ONNX model locations
    public static final String YOLO_ONNX_PATH;
    public static final String YOLO_FACE_ONNX_PATH;
    public static final String YOLO_POSE_ONNX_PATH;
    public static final String YOLO_SEG_ONNX_PATH;
    public static final String YOLO_OBB_ONNX_PATH;
    public static final String FAST_SAM_ONNX;

    // Detection configuration
    public static final Integer FRAME_INTERVAL;
    public static final float CONF_THRESHOLD;
    public static final float POSE_CONF_THRESHOLD;
    public static final float DETECT_RATIO;
    public static final float BLOCK_RATIO;
    public static final float NMS_THRESHOLD;
    public static final Boolean USE_GPU;

    public static List<Integer> YOLO_TYPES = new ArrayList<>();

    public static final Boolean TOKEN_FILTER;

    static {
        Properties properties = new Properties();
        InputStream input = null;
        try {
            input = Constant.class.getClassLoader().getResourceAsStream("application.properties");
            if (input == null) {
                throw new RuntimeException("application.properties not found");
            }
            properties.load(input);
            String envPath = System.getenv("VISION_MIND_PATH");
            boolean skipNativeConfig = Boolean.parseBoolean(System.getProperty("vision-mind.skip-opencv", "false"));
            if (envPath == null) {
                if (!skipNativeConfig && !isTestEnvironment()) {
                    log.warn("VISION_MIND_PATH is not defined. Native resources will be resolved relative to the current directory.");
                }
                envPath = "";
            }

            OPENCV_DLL_PATH = envPath + properties.getProperty("opencv.dll.path");
            OPENCV_SO_PATH = envPath + properties.getProperty("opencv.so.path");

            YOLO_ONNX_PATH = envPath + properties.getProperty("yolo.onnx.path");
            YOLO_FACE_ONNX_PATH = envPath + properties.getProperty("yolo.face.onnx.path");
            YOLO_POSE_ONNX_PATH = envPath + properties.getProperty("yolo.pose.onnx.path");
            FAST_SAM_ONNX = envPath + properties.getProperty("yolo.sam.onnx.path");
            YOLO_SEG_ONNX_PATH = envPath + properties.getProperty("yolo.seg.onnx.path");
            YOLO_OBB_ONNX_PATH = envPath + properties.getProperty("yolo.obb.onnx.path");

            FRAME_INTERVAL = Integer.parseInt(properties.getProperty("frame.interval"));
            CONF_THRESHOLD = Float.parseFloat(properties.getProperty("yolo.conf.Threshold"));
            POSE_CONF_THRESHOLD = Float.parseFloat(properties.getProperty("yolo.pose.conf.Threshold"));
            NMS_THRESHOLD = Float.parseFloat(properties.getProperty("yolo.nms.Threshold"));
            SAM_CONF = Float.parseFloat(properties.getProperty("sam.nms.Threshold"));
            DETECT_RATIO = Float.parseFloat(properties.getProperty("detect.ratio"));
            BLOCK_RATIO = Float.parseFloat(properties.getProperty("block.ratio"));
            USE_GPU = Boolean.valueOf(properties.getProperty("use.gpu"));
            TOKEN_FILTER = Boolean.valueOf(properties.getProperty("token.filter"));

            String types = properties.getProperty("yolo.types");
            if (types != null) {
                for (String type : types.split(",")) {
                    YOLO_TYPES.add(Integer.parseInt(type.trim()));
                }
            }

        } catch (IOException e) {
            throw new RuntimeException("Failed to read configuration file", e);
        } finally {
            if (input != null) {
                try {
                    input.close();
                } catch (IOException e) {
                    log.error("read application.properties error", e);
                }
            }
        }
    }

    private static boolean isTestEnvironment() {
        try {
            Class.forName("org.junit.jupiter.api.Test");
            return true;
        } catch (ClassNotFoundException ex) {
            return false;
        }
    }
}
