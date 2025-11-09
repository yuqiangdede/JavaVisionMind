package com.yuqiangdede.common;

import com.yuqiangdede.common.util.MatUtil;
import lombok.extern.slf4j.Slf4j;
import org.opencv.core.Mat;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.opencv.photo.Photo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Standalone OpenCV denoising demo that showcases three preset modes.
 * The input image path is defined via {@link #INPUT_IMAGE} and resolved relative to VISION_MIND_PATH.
 */
@Slf4j
public class OpenCvDenoiseDemo {

    private static final AtomicBoolean NATIVE_LOADED = new AtomicBoolean(false);

    private static final String RESOURCE_ROOT = System.getenv("VISION_MIND_PATH");
    private static final String OPENCV_DLL_PATH = "lib/opencv/opencv_java490.dll";
    private static final String OPENCV_SO_PATH = "lib/opencv/libopencv_java4100.so";
    private static final String INPUT_IMAGE = "demo/noisy-sample.png";
    private static final String OUTPUT_DIR = "demo/output";

    private enum Mode {
        FAST_NL_MEANS_GRAY(new DenoiseParameters(10.0f, 10.0f, 7, 21, 0, 0.0, 0.0)),
        FAST_NL_MEANS_COLOR(new DenoiseParameters(12.0f, 10.0f, 9, 35, 0, 0.0, 0.0)),
        BILATERAL_FILTER(new DenoiseParameters(5.0f, 0.0f, 0, 0, 9, 75.0, 75.0));

        private final DenoiseParameters parameters;

        Mode(DenoiseParameters parameters) {
            this.parameters = parameters;
        }

        public DenoiseParameters parameters() {
            return parameters;
        }
    }

    private record DenoiseParameters(
            float h,
            float hColor,
            int templateWindowSize,
            int searchWindowSize,
            int bilateralDiameter,
            double bilateralSigmaColor,
            double bilateralSigmaSpace
    ) {
    }

    public static void main(String[] args) throws IOException {
        loadOpenCvNative();

        Path baseDir = resolveBaseDir();
        Path inputPath = baseDir.resolve(INPUT_IMAGE).normalize();
        log.info("Input file: {}", inputPath.toAbsolutePath());

        if (!Files.exists(inputPath)) {
            log.error("Input image does not exist: {}", inputPath.toAbsolutePath());
            return;
        }

        Mat source = Imgcodecs.imread(inputPath.toString());
        if (source.empty()) {
            log.error("OpenCV failed to read image: {}", inputPath.toAbsolutePath());
            return;
        }

        try {
            for (Mode mode : Mode.values()) {
                Mat denoised = applyDenoise(source, mode);
                try {
                    Path outputPath = resolveOutputPath(baseDir, inputPath.getFileName().toString(), mode);
                    Files.createDirectories(outputPath.getParent());
                    boolean saved = Imgcodecs.imwrite(outputPath.toString(), denoised);
                    if (!saved) {
                        log.error("Failed to write denoised image: {}", outputPath.toAbsolutePath());
                        continue;
                    }
                    log.info("Mode {} -> {}", mode.name(), outputPath.toAbsolutePath());
                    String preview = MatUtil.matToBase64(denoised);
                    int previewLength = Math.min(120, preview.length());
                    log.info("Result image Base64 preview (first {} chars): {}", previewLength, preview.substring(0, previewLength));
                } finally {
                    denoised.release();
                }
            }
        } finally {
            source.release();
        }
    }

    private static Path resolveBaseDir() {
        if (RESOURCE_ROOT != null && !RESOURCE_ROOT.isBlank()) {
            return Paths.get(RESOURCE_ROOT).toAbsolutePath().normalize();
        }
        return Paths.get(".").toAbsolutePath().normalize();
    }

    private static Path resolveOutputPath(Path baseDir, String originalFileName, Mode mode) {
        int dotIndex = originalFileName.lastIndexOf('.');
        String suffix = dotIndex > 0 ? originalFileName.substring(dotIndex) : ".png";
        String prefix = dotIndex > 0 ? originalFileName.substring(0, dotIndex) : originalFileName;
        String fileName = prefix + "_" + mode.name().toLowerCase(Locale.ROOT) + suffix;
        return baseDir.resolve(OUTPUT_DIR).resolve(fileName).normalize();
    }

    private static Mat applyDenoise(Mat source, Mode mode) {
        Objects.requireNonNull(source, "Source image must not be null");
        Objects.requireNonNull(mode, "Mode must not be null");

        DenoiseParameters parameters = mode.parameters();
        Mat result = new Mat();
        switch (mode) {
            case FAST_NL_MEANS_GRAY -> {
                Mat gray = new Mat();
                try {
                    Imgproc.cvtColor(source, gray, Imgproc.COLOR_BGR2GRAY);
                    Photo.fastNlMeansDenoising(
                            gray,
                            result,
                            parameters.h(),
                            parameters.templateWindowSize(),
                            parameters.searchWindowSize()
                    );
                    Mat converted = new Mat();
                    Imgproc.cvtColor(result, converted, Imgproc.COLOR_GRAY2BGR);
                    result.release();
                    result = converted;
                } finally {
                    gray.release();
                }
            }
            case FAST_NL_MEANS_COLOR -> Photo.fastNlMeansDenoisingColored(
                    source,
                    result,
                    parameters.h(),
                    parameters.hColor(),
                    parameters.templateWindowSize(),
                    parameters.searchWindowSize()
            );
            case BILATERAL_FILTER -> Imgproc.bilateralFilter(
                    source,
                    result,
                    parameters.bilateralDiameter(),
                    parameters.bilateralSigmaColor(),
                    parameters.bilateralSigmaSpace()
            );
        }
        return result;
    }

    private static void loadOpenCvNative() {
        boolean skip = Boolean.parseBoolean(System.getProperty("vision-mind.skip-opencv", "false"));
        if (skip) {
            log.warn("Skip OpenCV native loading because vision-mind.skip-opencv is true");
            return;
        }
        if (NATIVE_LOADED.get()) {
            return;
        }

        synchronized (NATIVE_LOADED) {
            if (NATIVE_LOADED.get()) {
                return;
            }

            UnsatisfiedLinkError lastError = null;
            Path baseDir = resolveBaseDir();
            for (String candidate : new String[]{
                    baseDir.resolve(OPENCV_DLL_PATH).toString(),
                    baseDir.resolve(OPENCV_SO_PATH).toString()
            }) {
                Path path = toPath(candidate);
                if (path == null) {
                    continue;
                }
                if (Files.exists(path)) {
                    try {
                        System.load(path.toAbsolutePath().toString());
                        log.info("Loaded OpenCV native library from {}", path.toAbsolutePath());
                        NATIVE_LOADED.set(true);
                        return;
                    } catch (UnsatisfiedLinkError error) {
                        lastError = error;
                        log.warn("Failed to load OpenCV from {}: {}", path.toAbsolutePath(), error.getMessage());
                    }
                } else {
                    log.debug("Candidate OpenCV path does not exist: {}", path.toAbsolutePath());
                }
            }

            for (String libName : new String[]{"opencv_java490", "opencv_java4100"}) {
                try {
                    System.loadLibrary(libName);
                    log.info("Loaded OpenCV via System.loadLibrary(\"{}\")", libName);
                    NATIVE_LOADED.set(true);
                    return;
                } catch (UnsatisfiedLinkError error) {
                    lastError = error;
                    log.debug("System.loadLibrary(\"{}\") failed: {}", libName, error.getMessage());
                }
            }

            throw new IllegalStateException("OpenCV native library could not be loaded, please verify VISION_MIND_PATH", lastError);
        }
    }

    private static Path toPath(String candidate) {
        if (candidate == null || candidate.isBlank()) {
            return null;
        }
        return Paths.get(candidate).toAbsolutePath().normalize();
    }
}
