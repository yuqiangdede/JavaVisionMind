package com.yuqiangdede.yolo.config;

import com.yuqiangdede.common.config.YamlConfig;
import com.yuqiangdede.common.util.RuntimeEnvironment;
import com.yuqiangdede.platform.common.config.VisionMindProperties;
import com.yuqiangdede.platform.common.resource.ResourcePathResolver;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

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
    public static final String YOLO_LP_ONNX_PATH;
    public static final String YOLO_SEG_ONNX_PATH;
    public static final String YOLO_OBB_ONNX_PATH;
    public static final String YOLO_TEXT_ONNX_PATH;
    public static final String YOLO_TEXT_FREE_ONNX_PATH;
    public static final String YOLO_TEXT_ENCODER_ONNX_PATH;
    public static final String YOLO_TEXT_TOKENIZER_PATH;
    public static final float YOLO_TEXT_PROMPT_SCALE;
    public static final String FAST_SAM_ONNX;


    // Detection configuration
    public static final Integer FRAME_INTERVAL;
    public static final float CONF_THRESHOLD;
    public static final float POSE_CONF_THRESHOLD;
    public static final float DETECT_RATIO;
    public static final float BLOCK_RATIO;
    public static final float NMS_THRESHOLD;
    public static final boolean YOLO_NMS_ENABLED;
    public static final boolean YOLO_FACE_NMS_ENABLED;
    public static final boolean YOLO_LP_NMS_ENABLED;
    public static final boolean YOLO_POSE_NMS_ENABLED;
    public static final boolean YOLO_SEG_NMS_ENABLED;
    public static final boolean YOLO_OBB_NMS_ENABLED;
    public static final boolean YOLO_TEXT_NMS_ENABLED;
    public static final boolean YOLO_SAM_NMS_ENABLED;
    public static final Boolean USE_GPU;


    public static List<Integer> YOLO_TYPES = new ArrayList<>();
    public static List<Integer> YOLO_OBB_TYPES = new ArrayList<>();


    static {
        YamlConfig config = YamlConfig.load(Constant.class);
        String envPath = System.getenv("VISION_MIND_PATH");
        boolean skipNativeConfig = RuntimeEnvironment.shouldSkipNativeLoad();
        Path resourceRoot;
        if (envPath == null || envPath.isBlank()) {
            if (!skipNativeConfig) {
                log.warn("VISION_MIND_PATH is not defined. Trying to locate resource directory from the project path.");
            }
            resourceRoot = new ResourcePathResolver(new VisionMindProperties()).resourceRoot();
        } else {
            resourceRoot = Path.of(envPath).toAbsolutePath().normalize();
        }

        OPENCV_DLL_PATH = resolveResourcePath(resourceRoot,
                config.get("vision-mind.native.dll-path", "/lib/opencv/opencv_java490.dll"));
        OPENCV_SO_PATH = resolveResourcePath(resourceRoot,
                config.get("vision-mind.native.so-path", "/lib/opencv/libopencv_java4100.so"));

        YOLO_ONNX_PATH = modelPath(config, resourceRoot, "detect", "/yolo/model/yolo26s.onnx");
        YOLO_FACE_ONNX_PATH = modelPath(config, resourceRoot, "face", "/yolo/model/yolo-face.onnx");
        YOLO_POSE_ONNX_PATH = modelPath(config, resourceRoot, "pose", "/yolo/model/yolo26s-pose.onnx");
        YOLO_LP_ONNX_PATH = modelPath(config, resourceRoot, "lp", "/yolo/model/yolo-lp-s.onnx");
        FAST_SAM_ONNX = modelPath(config, resourceRoot, "sam", "/yolo/model/FastSAM-s.onnx");
        YOLO_SEG_ONNX_PATH = modelPath(config, resourceRoot, "segmentation", "/yolo/model/yolo26s-seg.onnx");
        YOLO_OBB_ONNX_PATH = modelPath(config, resourceRoot, "obb", "/yolo/model/yolo26s-obb.onnx");
        YOLO_TEXT_ONNX_PATH = modelPath(config, resourceRoot, "text", "/yolo/model/yoloe-26s-seg.onnx");
        YOLO_TEXT_FREE_ONNX_PATH = modelPath(config, resourceRoot, "text-free", "/yolo/model/yoloe-26s-seg-pf.onnx");
        YOLO_TEXT_ENCODER_ONNX_PATH = modelPath(config, resourceRoot, "text-encoder", "/yolo/model/mobileclip2_b.onnx");
        YOLO_TEXT_TOKENIZER_PATH = modelPath(config, resourceRoot, "text-tokenizer", "/tbir/clip-tokenizer");
        YOLO_TEXT_PROMPT_SCALE = config.getFloat("vision-mind.yolo.models.prompt-scale", 2.726257f);

        FRAME_INTERVAL = config.getInt("vision-mind.yolo.video.frame-interval", 5);
        CONF_THRESHOLD = config.getFloat("vision-mind.yolo.confidence-threshold", 0.3f);
        POSE_CONF_THRESHOLD = config.getFloat("vision-mind.yolo.pose-confidence-threshold", 0.3f);
        NMS_THRESHOLD = config.getFloat("vision-mind.yolo.nms-threshold", 0.3f);
        YOLO_NMS_ENABLED = config.getBoolean("vision-mind.yolo.nms.detect", false);
        YOLO_FACE_NMS_ENABLED = config.getBoolean("vision-mind.yolo.nms.face", true);
        YOLO_LP_NMS_ENABLED = config.getBoolean("vision-mind.yolo.nms.lp", true);
        YOLO_POSE_NMS_ENABLED = config.getBoolean("vision-mind.yolo.nms.pose", false);
        YOLO_SEG_NMS_ENABLED = config.getBoolean("vision-mind.yolo.nms.segmentation", false);
        YOLO_OBB_NMS_ENABLED = config.getBoolean("vision-mind.yolo.nms.obb", false);
        YOLO_TEXT_NMS_ENABLED = config.getBoolean("vision-mind.yolo.nms.text", true);
        YOLO_SAM_NMS_ENABLED = config.getBoolean("vision-mind.yolo.nms.sam", true);
        SAM_CONF = config.getFloat("vision-mind.yolo.sam-confidence-threshold", 0.5f);
        DETECT_RATIO = config.getFloat("vision-mind.yolo.detection-ratio", 0.5f);
        BLOCK_RATIO = config.getFloat("vision-mind.yolo.blocking-ratio", 0.5f);
        USE_GPU = config.getBoolean("vision-mind.native.use-gpu", false);

        YOLO_TYPES.addAll(config.getIntegerList("vision-mind.yolo.default-types"));
        YOLO_OBB_TYPES.addAll(config.getIntegerList("vision-mind.yolo.obb-default-types"));

        log.info("NMS enable flags: yolo={}, face={}, lp={}, pose={}, seg={}, obb={}, text={}, sam={}",
                YOLO_NMS_ENABLED, YOLO_FACE_NMS_ENABLED, YOLO_LP_NMS_ENABLED, YOLO_POSE_NMS_ENABLED,
                YOLO_SEG_NMS_ENABLED, YOLO_OBB_NMS_ENABLED, YOLO_TEXT_NMS_ENABLED, YOLO_SAM_NMS_ENABLED);
    }

    private static String modelPath(YamlConfig config, Path resourceRoot, String model,
                                    String defaultPath) {
        return resolveResourcePath(resourceRoot, config.get("vision-mind.yolo.models." + model, defaultPath));
    }

    private static String resolveResourcePath(Path resourceRoot, String configuredPath) {
        String relativePath = configuredPath.replace('\\', '/');
        while (relativePath.startsWith("/")) {
            relativePath = relativePath.substring(1);
        }
        return resourceRoot.resolve(relativePath).normalize().toString();
    }

}
