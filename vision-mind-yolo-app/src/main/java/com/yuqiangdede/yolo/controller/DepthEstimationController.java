package com.yuqiangdede.yolo.controller;

import ai.onnxruntime.OrtException;
import com.yuqiangdede.common.dto.output.HttpResult;
import com.yuqiangdede.yolo.dto.input.DepthEstimationRequest;
import com.yuqiangdede.yolo.dto.output.DepthEstimationResult;
import com.yuqiangdede.yolo.service.DepthEstimationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * YOLO26 单目深度估计接口。
 */
@RestController
@Slf4j
@RequestMapping("/api")
@RequiredArgsConstructor
public class DepthEstimationController {

    private final DepthEstimationService depthEstimationService;

    /**
     * 返回与原图对齐的逐像素米制深度图。
     */
    @PostMapping(value = {"/v1/img/depth", "/v1/vision/depth"},
            consumes = "application/json", produces = "application/json")
    public HttpResult<DepthEstimationResult> estimate(@RequestBody DepthEstimationRequest request) {
        long startTime = System.currentTimeMillis();
        if (request == null || !StringUtils.hasText(request.getImgUrl())) {
            return new HttpResult<>(false, "imgurl is null or empty");
        }
        try {
            DepthEstimationResult result = depthEstimationService.estimate(request);
            log.info("Depth estimation completed: image={}x{}, validPixels={}, range=[{}, {}]m, cost={}ms",
                    result.getWidth(), result.getHeight(), result.getValidPixelCount(),
                    result.getMinDepth(), result.getMaxDepth(), System.currentTimeMillis() - startTime);
            if (Boolean.TRUE.equals(request.getIncludeDepthMap())) {
                depthEstimationService.validateJsonDepthMap(result);
            } else {
                result.setDepthMap(null);
            }
            return new HttpResult<>(true, result);
        } catch (IOException | OrtException | RuntimeException ex) {
            log.error("depth estimation error", ex);
            return new HttpResult<>(false, "depth estimation failed");
        }
    }

    /**
     * 返回 little-endian float32、按行优先排列的原始米制深度图。
     */
    @PostMapping(value = {"/v1/img/depth/map", "/v1/vision/depth/map"},
            consumes = "application/json")
    public Object depthMap(@RequestBody DepthEstimationRequest request) {
        long startTime = System.currentTimeMillis();
        if (request == null || !StringUtils.hasText(request.getImgUrl())) {
            return binaryFailure("imgurl is null or empty");
        }
        try {
            DepthEstimationResult result = depthEstimationService.estimate(request);
            int responseBytes = Math.multiplyExact(result.getDepthMap().length, Float.BYTES);
            ByteBuffer buffer = ByteBuffer.allocate(responseBytes)
                    .order(ByteOrder.LITTLE_ENDIAN);
            for (float depth : result.getDepthMap()) {
                buffer.putFloat(depth);
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.add("X-Depth-Width", Integer.toString(result.getWidth()));
            headers.add("X-Depth-Height", Integer.toString(result.getHeight()));
            headers.add("X-Depth-Unit", result.getUnit());
            headers.add("X-Depth-Dtype", "float32-le");
            headers.add("X-Depth-Layout", result.getLayout());
            headers.add("X-Depth-Min", Float.toString(result.getMinDepth()));
            headers.add("X-Depth-Max", Float.toString(result.getMaxDepth()));
            headers.add("X-Depth-Mean", Float.toString(result.getMeanDepth()));
            headers.add("X-Depth-Median", Float.toString(result.getMedianDepth()));
            log.info("Depth map completed: image={}x{}, bytes={}, cost={}ms",
                    result.getWidth(), result.getHeight(), buffer.capacity(),
                    System.currentTimeMillis() - startTime);
            return new ResponseEntity<>(buffer.array(), headers, HttpStatus.OK);
        } catch (IOException | OrtException | RuntimeException ex) {
            log.error("depth map error", ex);
            return binaryFailure("depth estimation failed");
        }
    }

    /**
     * 返回 PNG 深度热力图叠加预览。
     */
    @PostMapping(value = {"/v1/img/depthI", "/v1/vision/depth/preview"},
            consumes = "application/json")
    public Object preview(@RequestBody DepthEstimationRequest request) {
        long startTime = System.currentTimeMillis();
        if (request == null || !StringUtils.hasText(request.getImgUrl())) {
            return binaryFailure("imgurl is null or empty");
        }
        try {
            BufferedImage image = depthEstimationService.preview(request);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            if (!ImageIO.write(image, "png", output)) {
                throw new IOException("PNG image writer is unavailable");
            }
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.IMAGE_PNG);
            log.info("Depth preview completed: image={}x{}, cost={}ms",
                    image.getWidth(), image.getHeight(), System.currentTimeMillis() - startTime);
            return new ResponseEntity<>(output.toByteArray(), headers, HttpStatus.OK);
        } catch (IOException | OrtException | RuntimeException ex) {
            log.error("depth preview error", ex);
            return binaryFailure("depth estimation failed");
        }
    }

    private ResponseEntity<HttpResult<Void>> binaryFailure(String message) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new ResponseEntity<>(new HttpResult<>(false, message), headers, HttpStatus.OK);
    }
}
