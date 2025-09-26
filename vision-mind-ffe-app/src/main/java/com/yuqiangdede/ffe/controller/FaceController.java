package com.yuqiangdede.ffe.controller;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.ObjectUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.yuqiangdede.common.dto.output.HttpResult;
import com.yuqiangdede.common.util.ImageUtil;
import com.yuqiangdede.ffe.core.domain.FaceImage;
import com.yuqiangdede.ffe.core.domain.FaceInfo;
import com.yuqiangdede.ffe.dto.input.Input4Compare;
import com.yuqiangdede.ffe.dto.input.Input4Del;
import com.yuqiangdede.ffe.dto.input.Input4Save;
import com.yuqiangdede.ffe.dto.input.Input4Search;
import com.yuqiangdede.ffe.dto.input.InputWithUrl;
import com.yuqiangdede.ffe.dto.output.FaceInfo4Add;
import com.yuqiangdede.ffe.dto.output.FaceInfo4Search;
import com.yuqiangdede.ffe.dto.output.FaceInfo4SearchAdd;
import com.yuqiangdede.ffe.service.FaceService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@Slf4j
@RequestMapping("/api")
@RequiredArgsConstructor
public class FaceController {
    private final FaceService faceService;

//    @PostMapping(value = "/v1/face/init", produces = "application/json", consumes = "application/json")
//    public HttpResult init(@RequestBody InitRequest indexPath) {
//        long start_time = System.currentTimeMillis();
//        if (ObjectUtils.isEmpty(indexPath.getIndexPath())) {
//            return new HttpResult<>(false, "id is null or empty");
//        }
//        try {
//            faceService.init(indexPath.getIndexPath());
//            log.info("init : {}. Cost time:{} ms.", indexPath, (System.currentTimeMillis() - start_time));
//            return new HttpResult<>(true, "success");
//        } catch (Exception e) {
//            log.error("delete error", e);
//            return new HttpResult<>(false, e.getMessage());
//        }
//    }

    @PostMapping(value = "/v1/face/computeFaceVector", produces = "application/json", consumes = "application/json")
    @SuppressWarnings("UseSpecificCatch")
    public HttpResult<FaceImage> computeFaceVector(@RequestBody InputWithUrl input) {
        long start_time = System.currentTimeMillis();
        if (ObjectUtils.isEmpty(input.getImgUrl())) {
            return new HttpResult<>(false, "imgurl is null or empty");
        }
        try {
            FaceImage faceImage = faceService.computeFaceVector(input);

            log.info("ComputeFaceVector : {}. {}. Cost time:{} ms.", input, faceImage, (System.currentTimeMillis() - start_time));
            return new HttpResult<>(true, "success", faceImage);
        } catch (Exception e) {
            log.error("computeAndSaveFaceVector error", e);
            return new HttpResult<>(false, e.getMessage());
        }
    }

    @PostMapping(value = "/v1/face/saveFaceVector", produces = "application/json", consumes = "application/json")
    @SuppressWarnings("UseSpecificCatch")
    public HttpResult<Object> saveFaceVector(@RequestBody Input4Save input) {
        long start_time = System.currentTimeMillis();
        if (ObjectUtils.isEmpty(input.getImgUrl())) {
            return new HttpResult<>(false, "imgurl is null or empty");
        }
        try {
            faceService.saveFaceVector(input);

            log.info("SaveFaceVector : {}.  Cost time:{} ms.", input, (System.currentTimeMillis() - start_time));
            return new HttpResult<>(true, "success");
        } catch (Exception e) {
            log.error("saveFaceVector error", e);
            return new HttpResult<>(false, e.getMessage());
        }
    }

    @PostMapping(value = "/v1/face/computeAndSaveFaceVector", produces = "application/json", consumes = "application/json")
    @SuppressWarnings("UseSpecificCatch")
    public HttpResult<List<FaceInfo4Add>> computeAndSaveFaceVector(@RequestBody InputWithUrl input) {
        long start_time = System.currentTimeMillis();
        if (ObjectUtils.isEmpty(input.getImgUrl())) {
            return new HttpResult<>(false, "imgurl is null or empty");
        }
        try {
            FaceImage faceImage = faceService.computeAndSaveFaceVector(input);

            List<FaceInfo4Add> result = new ArrayList<>();
            for (FaceInfo faceInfo : faceImage.getFaceInfos()) {
                result.add(new FaceInfo4Add(faceInfo));
            }
            log.info("ComputeAndSaveFaceVector : {}. {}. Cost time:{} ms.", input, result, (System.currentTimeMillis() - start_time));
            return new HttpResult<>(true, "success", result);
        } catch (Exception e) {
            log.error("computeAndSaveFaceVector error", e);
            return new HttpResult<>(false, e.getMessage());
        }
    }

    @PostMapping(value = "/v1/face/deleteFace", produces = "application/json", consumes = "application/json")
    @SuppressWarnings("UseSpecificCatch")
    public HttpResult<Object> deleteFace(@RequestBody Input4Del input) {
        long start_time = System.currentTimeMillis();
        if (ObjectUtils.isEmpty(input.getId())) {
            return new HttpResult<>(false, "id is null or empty");
        }
        try {
            faceService.delete(input);
            log.info("Delete Face : {}. Cost time:{} ms.", input, (System.currentTimeMillis() - start_time));
            return new HttpResult<>(true, "success");
        } catch (Exception e) {
            log.error("delete error", e);
            return new HttpResult<>(false, e.getMessage());
        }
    }

    @PostMapping(value = "/v1/face/findMostSimilarFace", produces = "application/json", consumes = "application/json")
    @SuppressWarnings("UseSpecificCatch")
    public HttpResult<List<FaceInfo4Search>> findMostSimilarFace(@RequestBody Input4Search input) {
        long start_time = System.currentTimeMillis();
        if (ObjectUtils.isEmpty(input.getImgUrl())) {
            return new HttpResult<>(false, "imgurl is null or empty");
        }
        try {
            List<FaceInfo4Search> result = faceService.findMostSimilarFace(input);

            log.info("FindMostSimilarFace : {} {}. Cost time:{} ms.", input, result, (System.currentTimeMillis() - start_time));
            return new HttpResult<>(true, "success", result);
        } catch (Exception e) {
            log.error("findMostSimilarFace error", e);
            return new HttpResult<>(false, e.getMessage());
        }
    }

    @PostMapping(value = "/v1/face/findMostSimilarFaceI", produces = "application/json", consumes = "application/json")
    @SuppressWarnings("UseSpecificCatch")
    public Object findMostSimilarFaceI(@RequestBody Input4Search input) {
        long start_time = System.currentTimeMillis();
        if (ObjectUtils.isEmpty(input.getImgUrl())) {
            return new HttpResult<>(false, "imgurl is null or empty");
        }

        BufferedImage image;
        try {
            List<FaceInfo4Search> result = faceService.findMostSimilarFace(input);
            String str = result.get(0).getImgUrl();
            image = ImageUtil.urlToImage(str);

            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            ImageIO.write(image, "jpg", byteArrayOutputStream);
            // 设置 HTTP 响应头
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.IMAGE_JPEG);
            log.info("findMostSimilarFaceI : {} {}. Cost time:{} ms.", input, result, (System.currentTimeMillis() - start_time));
            return new ResponseEntity<>(byteArrayOutputStream.toByteArray(), headers, HttpStatus.OK);
        } catch (Exception e) {
            log.error("findMostSimilarFaceI error", e);
            return new HttpResult<>(false, e.getMessage());
        }
    }

    /**
     * 比较两张人脸图片的相似度
     *
     * @param input 包含两张图片URL的请求体
     * @return HttpResult对象，包含操作结果和相似度信息(注意这个相似度和搜索中的相似度不可比较，这个是代码算的，搜索的那个是用Lucene底层算的)
     */
    @PostMapping(value = "/v1/face/calculateSimilarity", produces = "application/json", consumes = "application/json")
    @SuppressWarnings("UseSpecificCatch")
    public HttpResult<Object> calculateSimilarity(@RequestBody Input4Compare input) {
        long start_time = System.currentTimeMillis();
        if (ObjectUtils.isEmpty(input.getImgUrl())) {
            return new HttpResult<>(false, "imgurl is null or empty");
        }
        if (ObjectUtils.isEmpty(input.getImgUrl2())) {
            return new HttpResult<>(false, "imgurl2 is null or empty");
        }
        try {
            double cosineSimilarity = faceService.calculateSimilarity(input);
            log.info("calculateSimilarity: {}. cosineSimilarity {}. Cost time:{} ms.", input, cosineSimilarity, (System.currentTimeMillis() - start_time));
            return new HttpResult<>(true, "success", cosineSimilarity);
        } catch (Exception e) {
            log.error("calculateSimilarity error", e);
            return new HttpResult<>(false, e.getMessage());
        }
    }

    /**
     * 向人脸搜索系统中添加新的人脸信息
     *
     * @param input 包含要添加的人脸信息的对象，若是找到了相似的人脸就返回，若是没有找到相似的就添加
     * @return 包含操作结果的HttpResult对象
     */
    @PostMapping(value = "/v1/face/findSave", produces = "application/json", consumes = "application/json")
    @SuppressWarnings("UseSpecificCatch")
    public HttpResult<FaceInfo4SearchAdd> findSave(@RequestBody Input4Search input) {
        long start_time = System.currentTimeMillis();
        if (ObjectUtils.isEmpty(input.getImgUrl())) {
            return new HttpResult<>(false, "imgurl is null or empty");
        }
        try {
            FaceInfo4SearchAdd result = faceService.findSave(input);

            log.info("findSave : {}. {}. Cost time:{} ms.", input, result, (System.currentTimeMillis() - start_time));
            return new HttpResult<>(true, "success", result);
        } catch (Exception e) {
            log.error("findSave error", e);
            return new HttpResult<>(false, e.getMessage());
        }
    }
}
