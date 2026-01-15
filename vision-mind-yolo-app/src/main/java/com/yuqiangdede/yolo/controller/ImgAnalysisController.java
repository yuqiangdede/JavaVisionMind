package com.yuqiangdede.yolo.controller;


import com.yuqiangdede.yolo.dto.input.DetectionRequest;
import com.yuqiangdede.yolo.dto.input.DetectionRequestWithArea;
import com.yuqiangdede.common.dto.output.Box;
import com.yuqiangdede.common.dto.output.BoxWithKeypoints;
import com.yuqiangdede.common.dto.output.HttpResult;
import com.yuqiangdede.common.util.JsonUtils;
import com.yuqiangdede.yolo.dto.output.ObbDetection;
import com.yuqiangdede.yolo.dto.output.SegDetection;
import com.yuqiangdede.yolo.service.ImgAnalysisService;

import ai.onnxruntime.OrtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.ObjectUtils;
import org.springframework.web.bind.annotation.*;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * 图片分析，结果同步返回。
 */
@RestController
@Slf4j
@RequestMapping("/api")
@RequiredArgsConstructor
public class ImgAnalysisController {

    private final ImgAnalysisService imgAnalysisService;

    /**
     * 指定区域目标检测，
     *
     * @param imgAreaInput 输入
     * @return 检测结果
     */
    @PostMapping(value = "/v1/img/detect", produces = "application/json", consumes = "application/json")

    public HttpResult<List<Box>> predictorArea(@RequestBody DetectionRequestWithArea imgAreaInput) {
        long start_time = System.currentTimeMillis();
        if (ObjectUtils.isEmpty(imgAreaInput.getImgUrl())) {
            return new HttpResult<>(false, "imgurl is null or empty");
        }
        try {

            List<Box> boxs = imgAnalysisService.detectArea(imgAreaInput);
            log.info("Img DetectArea : Input：{}.Result:{}.Cost time：{} ms.", imgAreaInput, JsonUtils.object2Json(boxs), (System.currentTimeMillis() - start_time));

            return new HttpResult<>(true, boxs);
        } catch (IOException | OrtException | RuntimeException e) {
            log.error("detect error", e);
            return new HttpResult<>(false, e.getMessage());
        }
    }

    /**
     * 指定区域目标检测，返回图片
     *
     * @param imgAreaInput 输入
     * @return 图片
     */
    @PostMapping(value = "/v1/img/detectI", consumes = "application/json", produces = "image/jpeg")

    public Object predictorAreaI(@RequestBody DetectionRequestWithArea imgAreaInput) {
        long start_time = System.currentTimeMillis();
        BufferedImage image;
        try {
            image = imgAnalysisService.detectAreaI(imgAreaInput);

            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            ImageIO.write(image, "jpg", byteArrayOutputStream);
            // 设置 HTTP 响应头
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.IMAGE_JPEG);
            log.info("Img DetectAreaI : Input：{}..Cost time：{} ms.", imgAreaInput, (System.currentTimeMillis() - start_time));

            // 返回图片数据
            return new ResponseEntity<>(byteArrayOutputStream.toByteArray(), headers, HttpStatus.OK);
        } catch (IOException | OrtException | RuntimeException e) {
            log.error("detect error", e);
            return new HttpResult<>(false, e.getMessage());
        }
    }

    /**
     * 指定区域人脸检测
     *
     * @param imgAreaInput 输入
     * @return 检测结果
     */
    @PostMapping(value = "/v1/img/detectFace", produces = "application/json", consumes = "application/json")

    public HttpResult<List<Box>> predictorFace(@RequestBody DetectionRequestWithArea imgAreaInput) {
        long start_time = System.currentTimeMillis();
        if (ObjectUtils.isEmpty(imgAreaInput.getImgUrl())) {
            return new HttpResult<>(false, "imgurl is null or empty");
        }
        try {

            List<Box> boxs = imgAnalysisService.detectFace(imgAreaInput);
            log.info("Img DetectFace : Input：{}.Result:{}.Cost time：{} ms.", imgAreaInput, JsonUtils.object2Json(boxs), (System.currentTimeMillis() - start_time));

            return new HttpResult<>(true, boxs);
        } catch (IOException | OrtException | RuntimeException e) {
            log.error("detect error", e);
            return new HttpResult<>(false, e.getMessage());
        }
    }

    @PostMapping(value = "/v1/img/detectFaceI", consumes = "application/json", produces = "image/jpeg")

    public Object predictorFaceI(@RequestBody DetectionRequestWithArea imgAreaInput) {
        long start_time = System.currentTimeMillis();
        BufferedImage image;
        try {
            image = imgAnalysisService.detectFaceI(imgAreaInput);

            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            ImageIO.write(image, "jpg", byteArrayOutputStream);
            // 设置 HTTP 响应头
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.IMAGE_JPEG);
            log.info("Img DetectFaceI : Input：{}..Cost time：{} ms.", imgAreaInput, (System.currentTimeMillis() - start_time));

            // 返回图片数据
            return new ResponseEntity<>(byteArrayOutputStream.toByteArray(), headers, HttpStatus.OK);
        } catch (IOException | OrtException | RuntimeException e) {
            log.error("detect error", e);
            return new HttpResult<>(false, e.getMessage());
        }
    }

    @PostMapping(value = "/v1/img/detectLP", produces = "application/json", consumes = "application/json")
    public HttpResult<List<Box>> detectLicensePlate(@RequestBody DetectionRequestWithArea imgAreaInput) {
        long startTime = System.currentTimeMillis();
        if (ObjectUtils.isEmpty(imgAreaInput.getImgUrl())) {
            return new HttpResult<>(false, "imgurl is null or empty");
        }
        try {
            List<Box> boxs = imgAnalysisService.detectLP(imgAreaInput);
            log.info("Img DetectLP : Input：{}.Result:{} Cost time：{} ms.", imgAreaInput, JsonUtils.object2Json(boxs), (System.currentTimeMillis() - startTime));
            return new HttpResult<>(true, boxs);
        } catch (IOException | OrtException | RuntimeException e) {
            log.error("detect error", e);
            return new HttpResult<>(false, e.getMessage());
        }
    }

    @PostMapping(value = "/v1/img/detectLPI", consumes = "application/json", produces = "image/jpeg")
    public Object detectLicensePlateI(@RequestBody DetectionRequestWithArea imgAreaInput) {
        long startTime = System.currentTimeMillis();
        BufferedImage image;
        try {
            image = imgAnalysisService.detectLPI(imgAreaInput);
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            ImageIO.write(image, "jpg", byteArrayOutputStream);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.IMAGE_JPEG);
            log.info("Img DetectLPI : Input：{}. Cost time：{} ms.", imgAreaInput, (System.currentTimeMillis() - startTime));
            return new ResponseEntity<>(byteArrayOutputStream.toByteArray(), headers, HttpStatus.OK);
        } catch (IOException | OrtException | RuntimeException e) {
            log.error("detect error", e);
            return new HttpResult<>(false, e.getMessage());
        }
    }

    /**
     * 指定区域姿态检测
     *
     * @param imgAreaInput 输入
     * @return 检测结果
     */
    @PostMapping(value = "/v1/img/pose", produces = "application/json", consumes = "application/json")

    public HttpResult<List<BoxWithKeypoints>> poseArea(@RequestBody DetectionRequestWithArea imgAreaInput) {
        long start_time = System.currentTimeMillis();
        if (ObjectUtils.isEmpty(imgAreaInput.getImgUrl())) {
            return new HttpResult<>(false, "imgurl is null or empty");
        }
        try {

            List<BoxWithKeypoints> boxs = imgAnalysisService.poseArea(imgAreaInput);
            log.info("Img PoseArea : Input：{}.Result:{}.Cost time：{} ms.", imgAreaInput, JsonUtils.object2Json(boxs), (System.currentTimeMillis() - start_time));

            return new HttpResult<>(true, boxs);
        } catch (IOException | OrtException | RuntimeException e) {
            log.error("pose error", e);
            return new HttpResult<>(false, e.getMessage());
        }
    }

    /**
     * 指定区域姿态检测，返回图片
     *
     * @param imgAreaInput 输入
     * @return 图片
     * @apiNote 对给定的图片进行指定矩形内的姿态检测，返回图片 imgUrl必填 conf可选 x1 y1 x2 y2 必填
     */
    @PostMapping(value = "/v1/img/poseI", consumes = "application/json", produces = "image/jpeg")

    public Object poseAreaI(@RequestBody DetectionRequestWithArea imgAreaInput) {
        long start_time = System.currentTimeMillis();
        BufferedImage image;
        try {
            image = imgAnalysisService.poseAreaI(imgAreaInput);

            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            ImageIO.write(image, "jpg", byteArrayOutputStream);
            // 设置 HTTP 响应头
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.IMAGE_JPEG);
            log.info("Img PoseAreaI : Input：{}.Cost time：{} ms.", imgAreaInput, (System.currentTimeMillis() - start_time));

            // 返回图片数据
            return new ResponseEntity<>(byteArrayOutputStream.toByteArray(), headers, HttpStatus.OK);
        } catch (IOException | OrtException | RuntimeException e) {
            log.error("pose error", e);
            return new HttpResult<>(false, e.getMessage());
        }
    }


    @PostMapping(value = "/v1/img/sam", produces = "application/json", consumes = "application/json")
    public HttpResult<List<Box>> samArea(@RequestBody DetectionRequest imgAreaInput) {
        long start_time = System.currentTimeMillis();
        if (ObjectUtils.isEmpty(imgAreaInput.getImgUrl())) {
            return new HttpResult<>(false, "imgurl is null or empty");
        }
        try {

            List<Box> boxs = imgAnalysisService.sam(imgAreaInput);
            log.info("Img samArea : Input：{}.Result:{}.Cost time：{} ms.", imgAreaInput, JsonUtils.object2Json(boxs), (System.currentTimeMillis() - start_time));

            return new HttpResult<>(true, boxs);
        } catch (IOException | OrtException | RuntimeException e) {
            log.error("pose error", e);
            return new HttpResult<>(false, e.getMessage());
        }
    }


    @PostMapping(value = "/v1/img/samI", consumes = "application/json", produces = "image/jpeg")
    public Object samI(@RequestBody DetectionRequest imgAreaInput) {
        long start_time = System.currentTimeMillis();
        BufferedImage image;
        try {
            image = imgAnalysisService.samI(imgAreaInput);

            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            ImageIO.write(image, "jpg", byteArrayOutputStream);
            // 设置 HTTP 响应头
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.IMAGE_JPEG);
            log.info("Img SamI : Input：{}.Cost time：{} ms.", imgAreaInput, (System.currentTimeMillis() - start_time));

            // 返回图片数据
            return new ResponseEntity<>(byteArrayOutputStream.toByteArray(), headers, HttpStatus.OK);
        } catch (IOException | OrtException | RuntimeException e) {
            log.error("pose error", e);
            return new HttpResult<>(false, e.getMessage());
        }
    }

    @PostMapping(value = "/v1/img/obb", produces = "application/json", consumes = "application/json")
    public HttpResult<List<ObbDetection>> obbArea(@RequestBody DetectionRequestWithArea imgAreaInput) {
        long start_time = System.currentTimeMillis();
        if (ObjectUtils.isEmpty(imgAreaInput.getImgUrl())) {
            return new HttpResult<>(false, "imgurl is null or empty");
        }
        try {
            List<ObbDetection> obbs = imgAnalysisService.obbArea(imgAreaInput);
            log.info("Img ObbArea : Input：{}.Result:{}.Cost time：{} ms.", imgAreaInput, JsonUtils.object2Json(obbs), (System.currentTimeMillis() - start_time));
            return new HttpResult<>(true, obbs);
        } catch (IOException | OrtException | RuntimeException e) {
            log.error("obb detect error", e);
            return new HttpResult<>(false, e.getMessage());
        }
    }

    @PostMapping(value = "/v1/img/obbI", consumes = "application/json", produces = "image/jpeg")
    public Object obbAreaI(@RequestBody DetectionRequestWithArea imgAreaInput) {
        long start_time = System.currentTimeMillis();
        BufferedImage image;
        try {
            image = imgAnalysisService.obbAreaI(imgAreaInput);
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            ImageIO.write(image, "jpg", byteArrayOutputStream);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.IMAGE_JPEG);
            log.info("Img ObbAreaI : Input：{}.Cost time：{} ms.", imgAreaInput, (System.currentTimeMillis() - start_time));
            return new ResponseEntity<>(byteArrayOutputStream.toByteArray(), headers, HttpStatus.OK);
        } catch (IOException | OrtException | RuntimeException e) {
            log.error("obb detect error", e);
            return new HttpResult<>(false, e.getMessage());
        }
    }
 
    @PostMapping(value = "/v1/img/segI", consumes = "application/json", produces = "image/jpeg")
    public Object segAreaI(@RequestBody DetectionRequestWithArea imgAreaInput) {

        long start_time = System.currentTimeMillis();
        if (ObjectUtils.isEmpty(imgAreaInput.getImgUrl())) {
            return new HttpResult<>(false, "imgurl is null or empty");
        }
        BufferedImage image;
        try {
            image = imgAnalysisService.segAreaI(imgAreaInput);
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            ImageIO.write(image, "jpg", byteArrayOutputStream);
            // 设置 HTTP 响应头
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.IMAGE_JPEG);
            log.info("Img segArea : Input：{}..Cost time：{} ms.", imgAreaInput, (System.currentTimeMillis() - start_time));
            return new ResponseEntity<>(byteArrayOutputStream.toByteArray(), headers, HttpStatus.OK);
        } catch (IOException | OrtException | RuntimeException e) {
            log.error("detect error", e);
            return new HttpResult<>(false, e.getMessage());
        }
    }

    @PostMapping(value = "/v1/img/seg", produces = "application/json", consumes = "application/json")
    public HttpResult<List<SegDetection>> segArea(@RequestBody DetectionRequestWithArea imgAreaInput) {
        long start_time = System.currentTimeMillis();
        if (ObjectUtils.isEmpty(imgAreaInput.getImgUrl())) {
            return new HttpResult<>(false, "imgurl is null or empty");
        }
        try {
            List<SegDetection> boxs = imgAnalysisService.segArea(imgAreaInput);
            log.info("Img segArea : Input：{}.Result:{}.Cost time：{} ms.", imgAreaInput, JsonUtils.object2Json(boxs), (System.currentTimeMillis() - start_time));
            return new HttpResult<>(true, boxs);
        } catch (IOException | OrtException | RuntimeException e) {
            log.error("segArea error", e);
            return new HttpResult<>(false, e.getMessage());
        }
    }

}
