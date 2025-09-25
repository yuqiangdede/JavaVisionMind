package com.yuqiangdede.reid.service;

import ai.onnxruntime.OrtException;
import com.yuqiangdede.common.dto.output.Box;
import com.yuqiangdede.common.util.ImageUtil;
import com.yuqiangdede.common.util.VectorUtil;
import com.yuqiangdede.reid.config.ReidConstant;
import com.yuqiangdede.reid.output.Feature;
import com.yuqiangdede.reid.output.Human;
import com.yuqiangdede.reid.util.ReidLuceneUtil;
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
        // 加载opencv需要的动态库
        String osName = System.getProperty("os.name").toLowerCase();
        if (osName.contains("win")) {
            System.load(Constant.OPENCV_DLL_PATH);
        } else if (osName.contains("nix") || osName.contains("nux") || osName.contains("aix")) {
            System.load(Constant.OPENCV_SO_PATH);
        } else {
            throw new UnsupportedOperationException("Unsupported operating system: " + osName);
        }

        try {
            ReidLuceneUtil.init(ReidConstant.LUCENE_PATH);
        } catch (Exception e) {
            throw new RuntimeException(e);
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
        // 将图片URL转换为Mat对象
        Mat mat = ImageUtil.urlToMat(url);
        Feature feature = ReidUtil.featureSingle(mat);
        feature.setUuid(UUID.randomUUID().toString()); // 只是获取特征,不存储，给一个随机
        return feature;
    }

    /**
     * 计算两张图片之间的相似度
     *
     * @param imgUrl1 第一张图片的URL地址
     * @param imgUrl2 第二张图片的URL地址
     * @return 返回两张图片之间的相似度，相似度值介于0到1之间，值越大表示相似度越高
     * @throws IOException  如果读取图片时出现I/O错误，将抛出此异常
     * @throws OrtException 如果在使用ONNX Runtime时发生异常，将抛出此异常
     */
    public Float calculateSimilarity(String imgUrl1, String imgUrl2) throws IOException, OrtException {
        Feature feature1 = this.featureSingle(imgUrl1);
        Feature feature2 = this.featureSingle(imgUrl2);
        double v = VectorUtil.calculateCosineSimilarity(feature1.getEmbeds(), feature2.getEmbeds());
        return (float) v;
    }

    /**
     * Detect all persons within the supplied image URL, crop each region, and return the
     * corresponding feature vectors. The vectors use randomly generated UUIDs because they are
     * not persisted by this operation.
     */
    public List<Feature> featureMulti(String url) throws Exception {
        BufferedImage image = ImageUtil.urlToImage(url);

        // 构造目标检测的入参
        DetectionRequestWithArea detectionRequest = new DetectionRequestWithArea();
        detectionRequest.setImgUrl(url);
        detectionRequest.setThreshold(0.5f);// TODO 后面改成从配置文件读取
        detectionRequest.setTypes("0");

        // 检测出有人的区域,拿到人员子图
        List<Box> boxes = imgAnalysisService.detectArea(detectionRequest);
        List<Feature> features = new ArrayList<>();
        for (Box box : boxes) {
            BufferedImage subImg = ImageUtil.cropExpand(image, box, 0.1f);// TODO 扩展比例后面改成从配置文件读取
            Mat mat = ImageUtil.imgToMat(subImg);
            Feature feature = ReidUtil.featureSingle(mat);
            feature.setUuid(UUID.randomUUID().toString()); // 给一个随机值
            features.add(feature);
        }
        return features;
    }

    /**
     * Extract a single feature from the provided image and write it to the Lucene index together
     * with optional camera and human identifiers so that future searches can filter by metadata.
     */
    public Feature storeSingle(String imgUrl, String cameraId, String humanId) throws IOException, OrtException {
        // 将图片URL转换为Mat对象
        Mat mat = ImageUtil.urlToMat(imgUrl);
        Feature feature = ReidUtil.featureSingle(mat);
        feature.setUuid(UUID.randomUUID().toString()); // 向量代表的图片的uuid
        ReidLuceneUtil.add(imgUrl, cameraId, humanId, feature);
        return feature;
    }


    /**
     * Search the Lucene gallery for the given probe image, optionally filtering by camera and
     * limiting the number of returned matches. The similarity threshold determines whether a hit
     * is considered relevant.
     */
    public List<Human> search(String imgUrl, String cameraId, Integer topN, float threshold) throws IOException, OrtException {
        Mat mat = ImageUtil.urlToMat(imgUrl);
        Feature feature = ReidUtil.featureSingle(mat);// fixme
        return ReidLuceneUtil.searchByVector(feature.getEmbeds(), cameraId, topN, threshold);
    }

    // 待细化，这里的 storeSingle search 内部走了两次ReidUtil.featureSingle，后续可以进行优化 只走一次 //TODO
    /**
     * Single-cover workflow: if the probe does not match any existing human above the given
     * threshold it will be inserted as a new cover image, otherwise the best match is returned.
     */
    public Human searchOrStore(String imgUrl, float threshold) throws IOException, OrtException {
        // 单封面的逻辑
        List<Human> humans = this.search(imgUrl, null, 1, threshold);// TODO 需要修改 增加监控点id的逻辑

        if (humans.isEmpty()) {
            // 没搜到人 就把传入的图片存起来
            Mat mat = ImageUtil.urlToMat(imgUrl);
            Feature feature = ReidUtil.featureSingle(mat);
            feature.setUuid(UUID.randomUUID().toString()); // 新入库的图片 生成一个uuid作为imageId，也顺便作为humanId
            ReidLuceneUtil.add(imgUrl, null, null, feature);
            // 返回这个存储的人员信息 ，自己作为封面图feature.getUuid() 既是imageId 又是humanId
            return new Human(feature.getUuid(), feature.getUuid(), imgUrl, 1, null, "new");
        } else {
            // 搜到了就把封面结果返回，传入的图片不存
            return humans.get(0);
        }

    }

    /**
     * Multi-cover workflow: even when a match is found, persist the new probe image and associate
     * it with the matched human so future searches can leverage the expanded cover set.
     */
    public Human associateStore(String imgUrl, float threshold) throws IOException, OrtException {
        // 多封面的了逻辑
        List<Human> humans = this.search(imgUrl, null, 1, threshold);// TODO 需要修改 增加监控点id的逻辑

        if (humans.isEmpty()) {
            // 没搜到人 就把传入的图片存起来
            Mat mat = ImageUtil.urlToMat(imgUrl);
            Feature feature = ReidUtil.featureSingle(mat);
            feature.setUuid(UUID.randomUUID().toString()); // 新入库的图片 生成一个uuid作为imageId，也顺便作为humanId
            ReidLuceneUtil.add(imgUrl, null, null, feature);
            // 返回这个存储的人员信息 ，自己作为封面图feature.getUuid() 既是imageId 又是humanId
            return new Human(feature.getUuid(), feature.getUuid(), imgUrl, 1, null, "new");
        } else {
            // 搜到了也要报传入的图片存储，关联到封面图上 然后把搜到的封面结果返回
            Mat mat = ImageUtil.urlToMat(imgUrl);
            Feature feature = ReidUtil.featureSingle(mat);
            feature.setUuid(UUID.randomUUID().toString()); // 新入库的图片 生成一个uuid作为imageId,但是封面关联字段humanId 给设置为搜到的图的humanId（注意不能是搜到图的imageId）
            ReidLuceneUtil.add(imgUrl, null, humans.get(0).getHumanId(), feature);

            return humans.get(0);
        }
    }
}
