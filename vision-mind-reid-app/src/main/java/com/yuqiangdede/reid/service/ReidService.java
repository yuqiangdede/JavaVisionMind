package com.yuqiangdede.reid.service;

import ai.onnxruntime.OrtException;
import com.yuqiangdede.common.dto.output.Box;
import com.yuqiangdede.common.util.ImageUtil;
import com.yuqiangdede.common.util.VectorUtil;
import com.yuqiangdede.common.vector.ElasticsearchConfig;
import com.yuqiangdede.reid.config.ReidConstant;
import com.yuqiangdede.reid.output.Feature;
import com.yuqiangdede.reid.output.Human;
import com.yuqiangdede.reid.util.ReidVectorStoreUtil;
import com.yuqiangdede.reid.util.ReidUtil;
import com.yuqiangdede.yolo.config.Constant;
import com.yuqiangdede.yolo.dto.input.DetectionRequestWithArea;
import com.yuqiangdede.yolo.service.ImgAnalysisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.opencv.core.Mat;
import org.springframework.stereotype.Service;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class ReidService {
    static {
        if (shouldSkipNativeInit()) {
            log.debug("Skipping ReID native initialization in test mode.");
        } else {
            String osName = System.getProperty("os.name").toLowerCase();
            if (osName.contains("win")) {
                System.load(Constant.OPENCV_DLL_PATH);
            } else if (osName.contains("nix") || osName.contains("nux") || osName.contains("aix")) {
                System.load(Constant.OPENCV_SO_PATH);
            } else {
                throw new UnsupportedOperationException("Unsupported operating system: " + osName);
            }

            try {
                ElasticsearchConfig esConfig = new ElasticsearchConfig(
                        ReidConstant.ES_URIS,
                        ReidConstant.ES_USERNAME,
                        ReidConstant.ES_PASSWORD,
                        ReidConstant.ES_API_KEY,
                        ReidConstant.ES_REID_INDEX);
                ReidVectorStoreUtil.init(ReidConstant.LUCENE_PATH, ReidConstant.VECTOR_STORE_MODE, esConfig);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static boolean shouldSkipNativeInit() {
        boolean skipProperty = Boolean.parseBoolean(System.getProperty("vision-mind.skip-opencv", "false"));
        return skipProperty || isTestEnvironment();
    }

    private static boolean isTestEnvironment() {
        try {
            Class.forName("org.junit.jupiter.api.Test");
            return true;
        } catch (ClassNotFoundException ex) {
            return false;
        }
    }

    private final ImgAnalysisService imgAnalysisService;

    /**
     * 根据给定的图片URL获取单个特征
     *
     * @param url 图片的URL地址
     * @return 返回一个包含图片特征信息的Feature对象
     * @throws IOException  如果在处理过程中发生输入输出异常
     * @throws OrtException 如果在处理过程中发生ORT异常
     */
    public Feature featureSingle(String url) throws IOException, OrtException {
        Mat mat = ImageUtil.urlToMat(url);
        Feature feature = ReidUtil.featureSingle(mat);
        feature.setUuid(UUID.randomUUID().toString());
        return feature;
    }

    public Float calculateSimilarity(String imgUrl1, String imgUrl2) throws IOException, OrtException {
        Feature feature1 = this.featureSingle(imgUrl1);
        Feature feature2 = this.featureSingle(imgUrl2);
        double v = VectorUtil.calculateCosineSimilarity(feature1.getEmbeds(), feature2.getEmbeds());
        return (float) v;
    }

    public List<Feature> featureMulti(String url) throws Exception {
        BufferedImage image = ImageUtil.urlToImage(url);

        DetectionRequestWithArea detectionRequest = new DetectionRequestWithArea();
        detectionRequest.setImgUrl(url);
        detectionRequest.setThreshold(0.5f);
        detectionRequest.setTypes("0");

        List<Box> boxes = imgAnalysisService.detectArea(detectionRequest);
        List<Feature> features = new ArrayList<>();
        for (Box box : boxes) {
            BufferedImage subImg = ImageUtil.cropExpand(image, box, 0.1f);
            Mat mat = ImageUtil.imgToMat(subImg);
            Feature feature = ReidUtil.featureSingle(mat);
            feature.setUuid(UUID.randomUUID().toString());
            features.add(feature);
        }
        return features;
    }

    public Feature storeSingle(String imgUrl, String cameraId, String humanId) throws IOException, OrtException {
        Mat mat = ImageUtil.urlToMat(imgUrl);
        Feature feature = ReidUtil.featureSingle(mat);
        feature.setUuid(UUID.randomUUID().toString());
        ReidVectorStoreUtil.add(imgUrl, cameraId, humanId, feature);
        return feature;
    }

    public List<Human> search(String imgUrl, String cameraId, Integer topN, float threshold) throws IOException, OrtException {
        Mat mat = ImageUtil.urlToMat(imgUrl);
        Feature feature = ReidUtil.featureSingle(mat);
        return ReidVectorStoreUtil.searchByVector(feature.getEmbeds(), cameraId, topN, threshold);
    }

    public Human searchOrStore(String imgUrl, float threshold) throws IOException, OrtException {
        List<Human> humans = this.search(imgUrl, null, 1, threshold);

        if (humans.isEmpty()) {
            Mat mat = ImageUtil.urlToMat(imgUrl);
            Feature feature = ReidUtil.featureSingle(mat);
            feature.setUuid(UUID.randomUUID().toString());
            ReidVectorStoreUtil.add(imgUrl, null, null, feature);
            return new Human(feature.getUuid(), feature.getUuid(), imgUrl, 1, null, "new");
        } else {
            return humans.get(0);
        }

    }

    public Human associateStore(String imgUrl, float threshold) throws IOException, OrtException {
        List<Human> humans = this.search(imgUrl, null, 1, threshold);

        if (humans.isEmpty()) {
            Mat mat = ImageUtil.urlToMat(imgUrl);
            Feature feature = ReidUtil.featureSingle(mat);
            feature.setUuid(UUID.randomUUID().toString());
            ReidVectorStoreUtil.add(imgUrl, null, null, feature);
            return new Human(feature.getUuid(), feature.getUuid(), imgUrl, 1, null, "new");
        } else {
            Mat mat = ImageUtil.urlToMat(imgUrl);
            Feature feature = ReidUtil.featureSingle(mat);
            feature.setUuid(UUID.randomUUID().toString());
            ReidVectorStoreUtil.add(imgUrl, null, humans.get(0).getHumanId(), feature);

            return humans.get(0);
        }
    }
}


