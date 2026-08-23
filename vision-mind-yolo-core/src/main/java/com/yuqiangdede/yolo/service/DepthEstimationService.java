package com.yuqiangdede.yolo.service;

import ai.onnxruntime.OrtException;
import com.yuqiangdede.platform.common.resource.ResourcePathResolver;
import com.yuqiangdede.yolo.dto.input.DepthEstimationRequest;
import com.yuqiangdede.yolo.dto.output.DepthEstimationResult;
import com.yuqiangdede.yolo.util.yolo.YoloV26DepthUtil;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * YOLO26 单目深度估计服务。
 */
@Service
@Slf4j
public class DepthEstimationService {

    private final YoloV26DepthUtil depthUtil;
    private final DepthImageLoader imageLoader;
    private final Semaphore inferencePermits;
    private final long queueTimeoutMs;
    private final long maxJsonPixels;

    public DepthEstimationService(ResourcePathResolver resourcePathResolver,
                                  @Value("${vision-mind.yolo.depth.model:yolo/model/yolo26n-depth.onnx}")
                                  String configuredModelPath,
                                  @Value("${vision-mind.yolo.depth.max-pixels:2100000}") long maxPixels,
                                  @Value("${vision-mind.yolo.depth.max-download-bytes:26214400}") int maxDownloadBytes,
                                  @Value("${vision-mind.yolo.depth.connect-timeout-ms:5000}") int connectTimeoutMs,
                                  @Value("${vision-mind.yolo.depth.http-total-timeout-ms:15000}") long totalTimeoutMs,
                                  @Value("${vision-mind.yolo.depth.max-redirects:3}") int maxRedirects,
                                  @Value("${vision-mind.yolo.depth.allow-local-files:false}") boolean allowLocalFiles,
                                  @Value("${vision-mind.yolo.depth.local-roots:}") String configuredLocalRoots,
                                  @Value("${vision-mind.yolo.depth.allow-data-uri:true}") boolean allowDataUri,
                                  @Value("${vision-mind.yolo.depth.allowed-remote-hosts:}") String configuredRemoteHosts,
                                  @Value("${vision-mind.yolo.depth.max-concurrent:1}") int maxConcurrent,
                                  @Value("${vision-mind.yolo.depth.queue-timeout-ms:5000}") long queueTimeoutMs,
                                  @Value("${vision-mind.yolo.depth.max-json-pixels:500000}") long maxJsonPixels) {
        if (maxConcurrent <= 0 || queueTimeoutMs < 0 || maxJsonPixels <= 0) {
            throw new IllegalArgumentException("depth concurrency and response limits are invalid");
        }
        Path modelPath = resourcePathResolver.resolve(configuredModelPath);
        this.depthUtil = new YoloV26DepthUtil(modelPath);
        this.imageLoader = new DepthImageLoader(maxPixels, maxDownloadBytes, connectTimeoutMs, totalTimeoutMs,
                maxRedirects, allowLocalFiles, parsePaths(configuredLocalRoots), allowDataUri,
                parseHosts(configuredRemoteHosts));
        this.inferencePermits = new Semaphore(maxConcurrent, true);
        this.queueTimeoutMs = queueTimeoutMs;
        this.maxJsonPixels = maxJsonPixels;
    }

    public DepthEstimationResult estimate(DepthEstimationRequest request) throws IOException, OrtException {
        return withInferencePermit(() -> depthUtil.estimate(imageLoader.load(request.getImgUrl())));
    }

    public BufferedImage preview(DepthEstimationRequest request) throws IOException, OrtException {
        return withInferencePermit(() -> {
            BufferedImage image = imageLoader.load(request.getImgUrl());
            DepthEstimationResult result = depthUtil.estimate(image);
            return depthUtil.renderPreview(image, result, request.getVisualizationMode(),
                    request.getMinDepth(), request.getMaxDepth());
        });
    }

    public void validateJsonDepthMap(DepthEstimationResult result) {
        long pixels = Math.multiplyExact((long) result.getWidth(), result.getHeight());
        if (pixels > maxJsonPixels) {
            throw new IllegalArgumentException("depth map is too large for JSON; use /depth/map");
        }
    }

    private <T> T withInferencePermit(InferenceOperation<T> operation) throws IOException, OrtException {
        boolean acquired;
        try {
            acquired = inferencePermits.tryAcquire(queueTimeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("depth inference was interrupted");
        }
        if (!acquired) {
            throw new IllegalStateException("depth inference is busy");
        }
        try {
            return operation.execute();
        } finally {
            inferencePermits.release();
        }
    }

    @PreDestroy
    public void shutdown() {
        try {
            depthUtil.close();
        } catch (OrtException ex) {
            log.warn("Failed to close YOLO26 depth session", ex);
        }
    }

    private static List<Path> parsePaths(String configuredPaths) {
        if (configuredPaths == null || configuredPaths.isBlank()) {
            return List.of();
        }
        return Arrays.stream(configuredPaths.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(Path::of)
                .toList();
    }

    private static Set<String> parseHosts(String configuredHosts) {
        if (configuredHosts == null || configuredHosts.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(configuredHosts.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    @FunctionalInterface
    private interface InferenceOperation<T> {

        T execute() throws IOException, OrtException;
    }
}
