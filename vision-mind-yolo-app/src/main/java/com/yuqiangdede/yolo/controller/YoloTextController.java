package com.yuqiangdede.yolo.controller;

import ai.onnxruntime.OrtException;
import com.yuqiangdede.common.dto.output.Box;
import com.yuqiangdede.common.dto.output.HttpResult;
import com.yuqiangdede.common.util.JsonUtils;
import com.yuqiangdede.yolo.dto.input.DetectionRequest;
import com.yuqiangdede.yolo.dto.input.TextPromptRequestWithArea;
import com.yuqiangdede.yolo.dto.output.SegDetection;
import com.yuqiangdede.yolo.service.ImgAnalysisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.ObjectUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * 文本提示目标检测。
 */
@RestController
@Slf4j
@RequestMapping("/api")
@RequiredArgsConstructor
public class YoloTextController {

    private final ImgAnalysisService imgAnalysisService;

    /**
     * 文本提示目标检测
     *
     * @param imgAreaInput 输入
     * @return 检测结果
     */
    @PostMapping(value = "/v1/yoloe/detectText", produces = "application/json", consumes = "application/json")
    public HttpResult<List<Box>> detectText(@RequestBody TextPromptRequestWithArea imgAreaInput) {
        long start_time = System.currentTimeMillis();
        if (ObjectUtils.isEmpty(imgAreaInput.getImgUrl())) {
            return new HttpResult<>(false, "imgurl is null or empty");
        }
        try {
            List<Box> boxs = imgAnalysisService.detectTextArea(imgAreaInput);
            log.info("Img DetectText : Input：{}.Result:{}.Cost time：{} ms.", imgAreaInput, JsonUtils.object2Json(boxs),
                    (System.currentTimeMillis() - start_time));
            return new HttpResult<>(true, boxs);
        } catch (IOException | OrtException | RuntimeException e) {
            log.error("detect text error", e);
            return new HttpResult<>(false, e.getMessage());
        }
    }

    /**
     * 文本提示目标检测，返回图片
     *
     * @param imgAreaInput 输入
     * @return 图片
     */
    @PostMapping(value = "/v1/yoloe/detectTextI", consumes = "application/json",
            produces = {"image/jpeg", "application/json"})
    public Object detectTextI(@RequestBody TextPromptRequestWithArea imgAreaInput) {
        long start_time = System.currentTimeMillis();
        BufferedImage image;
        try {
            if (ObjectUtils.isEmpty(imgAreaInput.getImgUrl())) {
                return new HttpResult<>(false, "imgurl is null or empty");
            }
            image = imgAnalysisService.detectTextAreaI(imgAreaInput);

            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            ImageIO.write(image, "jpg", byteArrayOutputStream);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.IMAGE_JPEG);
            log.info("Img DetectTextI : Input：{}..Cost time：{} ms.", imgAreaInput, (System.currentTimeMillis() - start_time));

            return new ResponseEntity<>(byteArrayOutputStream.toByteArray(), headers, HttpStatus.OK);
        } catch (IOException | OrtException | RuntimeException e) {
            log.error("detect text error", e);
            return new HttpResult<>(false, e.getMessage());
        }
    }

    /**
     * Prompt-free 目标检测
     *
     * @param imgInput 输入
     * @return 检测结果
     */
    @PostMapping(value = "/v1/yoloe/detectFree", produces = "application/json", consumes = "application/json")
    public HttpResult<List<SegDetection>> detectFree(@RequestBody DetectionRequest imgInput) {
        long start_time = System.currentTimeMillis();
        if (ObjectUtils.isEmpty(imgInput.getImgUrl())) {
            return new HttpResult<>(false, "imgurl is null or empty");
        }
        try {
            List<SegDetection> segs = imgAnalysisService.detectFree(imgInput);
            log.info("Img DetectFree : Input：{}.Result:{}.Cost time：{} ms.", imgInput, JsonUtils.object2Json(segs),
                    (System.currentTimeMillis() - start_time));
            return new HttpResult<>(true, segs);
        } catch (IOException | OrtException | RuntimeException e) {
            log.error("detect free error", e);
            return new HttpResult<>(false, e.getMessage());
        }
    }

    /**
     * Prompt-free 目标检测，返回图片
     *
     * @param imgInput 输入
     * @return 图片
     */
    @PostMapping(value = "/v1/yoloe/detectFreeI", consumes = "application/json",
            produces = {"image/jpeg", "application/json"})
    public Object detectFreeI(@RequestBody DetectionRequest imgInput) {
        long start_time = System.currentTimeMillis();
        BufferedImage image;
        try {
            if (ObjectUtils.isEmpty(imgInput.getImgUrl())) {
                return new HttpResult<>(false, "imgurl is null or empty");
            }
            image = imgAnalysisService.detectFreeI(imgInput);

            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            ImageIO.write(image, "jpg", byteArrayOutputStream);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.IMAGE_JPEG);
            log.info("Img DetectFreeI : Input：{}..Cost time：{} ms.", imgInput, (System.currentTimeMillis() - start_time));

            return new ResponseEntity<>(byteArrayOutputStream.toByteArray(), headers, HttpStatus.OK);
        } catch (IOException | OrtException | RuntimeException e) {
            log.error("detect free error", e);
            return new HttpResult<>(false, e.getMessage());
        }
    }
}
