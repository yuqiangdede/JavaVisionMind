package com.yuqiangdede.rfdetr.controller;

import com.yuqiangdede.common.dto.output.Box;
import com.yuqiangdede.common.dto.output.HttpResult;
import com.yuqiangdede.rfdetr.dto.input.DetectionRequestWithArea;
import com.yuqiangdede.rfdetr.service.RfDetrImageAnalysisService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

@RestController
@Slf4j
@RequestMapping("/api")
@RequiredArgsConstructor
public class ImgAnalysisController {

    private final RfDetrImageAnalysisService imageAnalysisService;

    @PostMapping(value = {"/v1/img/detect", "/v1/vision/detect"}, consumes = "application/json", produces = "application/json")
    public HttpResult<List<Box>> detect(@RequestBody DetectionRequestWithArea request) {
        if (request == null || !StringUtils.hasText(request.getImgUrl())) {
            return new HttpResult<>(false, "imgurl is null or empty");
        }
        try {
            List<Box> boxes = imageAnalysisService.detectArea(request);
            log.info("RF-DETR image detect completed: boxSize={}", boxes.size());
            return new HttpResult<>(true, boxes);
        } catch (IOException | RuntimeException ex) {
            log.error("RF-DETR image detect failed", ex);
            return new HttpResult<>(false, ex.getMessage());
        }
    }

    @PostMapping(value = {"/v1/img/detectI", "/v1/vision/detect/preview"}, consumes = "application/json", produces = "image/jpeg")
    public Object detectPreview(@RequestBody DetectionRequestWithArea request) {
        if (request == null || !StringUtils.hasText(request.getImgUrl())) {
            return new HttpResult<>(false, "imgurl is null or empty");
        }
        try {
            BufferedImage image = imageAnalysisService.detectAreaPreview(request);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            ImageIO.write(image, "jpg", output);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.IMAGE_JPEG);
            return new ResponseEntity<>(output.toByteArray(), headers, HttpStatus.OK);
        } catch (IOException | RuntimeException ex) {
            log.error("RF-DETR image preview failed", ex);
            return new HttpResult<>(false, ex.getMessage());
        }
    }

    @PostMapping(value = "/v1/vision/detect/upload", consumes = "multipart/form-data", produces = "application/json")
    public HttpResult<List<Box>> detectUpload(@RequestParam("file") MultipartFile file,
                                              @RequestParam(value = "threshold", required = false) Float threshold,
                                              @RequestParam(value = "types", required = false) String types) {
        try {
            if (file == null || file.isEmpty()) {
                return new HttpResult<>(false, "upload file is empty");
            }
            BufferedImage image = ImageIO.read(file.getInputStream());
            if (image == null) {
                return new HttpResult<>(false, "unsupported upload image");
            }
            return new HttpResult<>(true, imageAnalysisService.detectUpload(image, threshold, types));
        } catch (IOException | RuntimeException ex) {
            log.error("RF-DETR upload detect failed", ex);
            return new HttpResult<>(false, ex.getMessage());
        }
    }
}
