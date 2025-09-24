package com.yuqiangdede.reid.controller;

import com.yuqiangdede.common.dto.output.HttpResult;
import com.yuqiangdede.reid.output.Feature;
import com.yuqiangdede.reid.output.Human;
import com.yuqiangdede.reid.service.ReidService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.util.ObjectUtils;

import java.util.List;
import java.util.Map;

@RestController
@Slf4j
@RequestMapping("/api")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ReidController {
    private final ReidService reidService;

    @PostMapping(value = "/v1/reid/feature/single", produces = "application/json", consumes = "application/json")
    public HttpResult<Feature> featureSingle(@RequestBody Map<String, String> input) {
        long start_time = System.currentTimeMillis();
        if (ObjectUtils.isEmpty(input.get("imgUrl"))) {
            return new HttpResult<>(false, "imgUrl is null or empty");
        }
        try {
            Feature feature = reidService.featureSingle(input.get("imgUrl"));
            log.info("featureSingle : {}. {}. Cost time:{} ms.", input, feature, (System.currentTimeMillis() - start_time));
            return new HttpResult<>(true, "success", feature);
        } catch (Exception e) {
            log.error("featureSingle error", e);
            return new HttpResult<>(false, e.getMessage());
        }
    }

    @PostMapping(value = "/v1/reid/feature/calculateSimilarity", produces = "application/json", consumes = "application/json")
    public HttpResult<Float> calculateSimilarity(@RequestBody Map<String, String> input) {
        long start_time = System.currentTimeMillis();
        if (ObjectUtils.isEmpty(input.get("imgUrl1")) || (ObjectUtils.isEmpty(input.get("imgUrl2")))) {
            return new HttpResult<>(false, "imgUrl is null or empty");
        }
        try {
            Float f = reidService.calculateSimilarity(input.get("imgUrl1"), input.get("imgUrl2"));

            log.info("calculateSimilarity : {}. {}. Cost time:{} ms.", input, f, (System.currentTimeMillis() - start_time));
            return new HttpResult<>(true, "success", f);
        } catch (Exception e) {
            log.error("calculateSimilarity error", e);
            return new HttpResult<>(false, e.getMessage());
        }
    }

    @PostMapping(value = "/v1/reid/feature/multi", produces = "application/json", consumes = "application/json")
    public HttpResult<List<Feature>> featureMulti(@RequestBody Map<String, String> input) {
        long start_time = System.currentTimeMillis();
        if (ObjectUtils.isEmpty(input.get("imgUrl"))) {
            return new HttpResult<>(false, "imgUrl is null or empty");
        }
        try {
            List<Feature> features = reidService.featureMulti(input.get("imgUrl"));
            log.info("featureMulti : {}. {}. Cost time:{} ms.", input, features, (System.currentTimeMillis() - start_time));
            return new HttpResult<>(true, "success", features);
        } catch (Exception e) {
            log.error("featureMulti error", e);
            return new HttpResult<>(false, e.getMessage());
        }
    }

    @PostMapping(value = "/v1/reid/store/single", produces = "application/json", consumes = "application/json")
    public HttpResult<Feature> storeSingle(@RequestBody Map<String, String> input) {
        long start_time = System.currentTimeMillis();
        if (ObjectUtils.isEmpty(input.get("imgUrl"))) {
            return new HttpResult<>(false, "imgUrl is null or empty");
        }
        try {
            Feature feature = reidService.storeSingle(input.get("imgUrl"), input.get("cameraId"), input.get("humanId"));
            log.info("storeSingle : {}. {}. Cost time:{} ms.", input, feature, (System.currentTimeMillis() - start_time));
            return new HttpResult<>(true, "success", feature);
        } catch (Exception e) {
            log.error("storeSingle error", e);
            return new HttpResult<>(false, e.getMessage());
        }
    }

//    @PostMapping(value = "/v1/reid/store/multi", produces = "application/json", consumes = "application/json")
//    public HttpResult storeMulti(@RequestBody Map input) {
//        long start_time = System.currentTimeMillis();
//        if (ObjectUtils.isEmpty(input.get("imgurl"))) {
//            return new HttpResult<>(false, "imgurl is null or empty");
//        }
//        try {
////            FaceImage faceImage = reidService.storeMulti(input.get("imgurl"));
//
//            log.info("featureSingle : {}. {}. Cost time:{} ms.", input, faceImage, (System.currentTimeMillis() - start_time));
//            return new HttpResult<>(true, "success", faceImage);
//        } catch (Exception e) {
//            log.error("featureSingle error", e);
//            return new HttpResult<>(false, e.getMessage());
//        }
//    }


    @PostMapping(value = "/v1/reid/search", produces = "application/json", consumes = "application/json")
    public HttpResult<List<Human>> search(@RequestBody Map<String, String> input) {
        long start_time = System.currentTimeMillis();
        if (ObjectUtils.isEmpty(input.get("imgUrl"))) {
            return new HttpResult<>(false, "imgUrl is null or empty");
        }
        try {
            List<Human> humans = reidService.search(input.get("imgUrl"), input.get("cameraId"), Integer.valueOf(input.get("topN")), Float.parseFloat(input.get("threshold")));
            log.info("search : {}. {}. Cost time:{} ms.", input, humans, (System.currentTimeMillis() - start_time));
            return new HttpResult<>(true, "success", humans);
        } catch (Exception e) {
            log.error("search error", e);
            return new HttpResult<>(false, e.getMessage());
        }
    }

    /**
     * 单封面聚类
     */
    @PostMapping(value = "/v1/reid/searchOrStore", produces = "application/json", consumes = "application/json")
    public HttpResult<Human> searchOrStore(@RequestBody Map<String, String> input) {
        long start_time = System.currentTimeMillis();
        if (ObjectUtils.isEmpty(input.get("imgUrl"))) {
            return new HttpResult<>(false, "imgUrl is null or empty");
        }
        try {
            Human human = reidService.searchOrStore(input.get("imgUrl"), Float.parseFloat(input.get("threshold")));
            log.info("searchOrStore : {}. {}. Cost time:{} ms.", input, human, (System.currentTimeMillis() - start_time));
            return new HttpResult<>(true, "success", human);
        } catch (Exception e) {
            log.error("searchOrStore error", e);
            return new HttpResult<>(false, e.getMessage());
        }
    }

    // associate-and-store 多封面
    @PostMapping(value = "/v1/reid/associateStore", produces = "application/json", consumes = "application/json")
    public HttpResult<Human> associateStore(@RequestBody Map<String, String> input) {
        long start_time = System.currentTimeMillis();
        if (ObjectUtils.isEmpty(input.get("imgUrl"))) {
            return new HttpResult<>(false, "imgUrl is null or empty");
        }
        try {
            Human human = reidService.associateStore(input.get("imgUrl"), Float.parseFloat(input.get("threshold")));
            log.info("associateStore : {}. {}. Cost time:{} ms.", input, human, (System.currentTimeMillis() - start_time));
            return new HttpResult<>(true, "success", human);
        } catch (Exception e) {
            log.error("associateStore error", e);
            return new HttpResult<>(false, e.getMessage());
        }
    }
}
