package com.yuqiangdede.ffe.service;

import com.yuqiangdede.common.util.ImageUtil;
import com.yuqiangdede.common.util.VectorUtil;
import com.yuqiangdede.ffe.config.Constant;
import com.yuqiangdede.ffe.core.base.*;
import com.yuqiangdede.ffe.core.domain.FaceImage;
import com.yuqiangdede.ffe.core.domain.FaceInfo;
import com.yuqiangdede.ffe.core.domain.ImageMat;
import com.yuqiangdede.ffe.core.extract.FaceFeatureExtractor;
import com.yuqiangdede.ffe.core.extract.FaceFeatureExtractorImpl;
import com.yuqiangdede.ffe.core.models.*;
import com.yuqiangdede.ffe.dto.input.*;
import com.yuqiangdede.ffe.dto.output.FaceInfo4Add;
import com.yuqiangdede.ffe.dto.output.FaceInfo4Search;
import com.yuqiangdede.ffe.dto.output.FaceInfo4SearchAdd;
import com.yuqiangdede.ffe.util.FfeLuceneUtil;
import lombok.extern.slf4j.Slf4j;
import org.opencv.core.Mat;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;

@Service
@Slf4j
public class FaceService {


    static FaceFeatureExtractor extractor;

    static {
        try {
            // 检测
            FaceDetection insightScrfdFaceDetection = new InsightScrfdFaceDetection(Constant.MODEL_SCRFD_PATH, 1);
            // 关键点
            FaceKeyPoint insightCoordFaceKeyPoint = new InsightCoordFaceKeyPoint(Constant.MODEL_COORD_PATH, 1);
            // 特征提取
            FaceRecognition insightArcFaceRecognition = new InsightArcFaceRecognition(Constant.MODEL_ARC_PATH, 1);
            // 对齐
            FaceAlignment simple106pFaceAlignment = new Simple106pFaceAlignment();
            // 属性分析
            FaceAttribute insightFaceAttribute = new InsightAttributeDetection(Constant.MODEL_ARR_PATH, 1);

            FfeLuceneUtil.init(Constant.LUCENE_PATH);

            extractor = new FaceFeatureExtractorImpl(
                    insightScrfdFaceDetection, insightCoordFaceKeyPoint,
                    simple106pFaceAlignment, insightArcFaceRecognition, insightFaceAttribute);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    /**
     * 添加输入图像并提取人脸特征，将提取到的人脸信息入库
     *
     * @param input 输入对象，包含图像URL等信息
     * @return 包含提取到的人脸信息的FaceImage对象
     * @throws IOException 如果在添加过程中发生异常，则抛出该异常
     */
    public FaceImage computeAndSaveFaceVector(InputWithUrl input) throws IOException {
        Mat mat = ImageUtil.urlToMat(input.getImgUrl());
        FaceImage faceImage = getFaceInfos(mat);
        List<FaceInfo> fs = faceImage.getFaceInfos();
        List<FaceInfo> faceInfos = new ArrayList<>();
        // 若有多个人脸就都入库
        for (FaceInfo faceInfo : fs) {
            // 大于设置的阈值的人脸才入库和返回，否则都丢掉
            if (faceInfo.getScore() > input.getFaceScoreThreshold()) {
                // 添加到索引库
                FfeLuceneUtil.add(faceInfo.getEmbedding().getEmbeds(), input.getImgUrl(), faceInfo.getId(), input.getGroupId());
                faceInfos.add(faceInfo);
            }
        }
        faceImage.setFaceInfos(faceInfos);
        return faceImage;
    }

    public void saveFaceVector(Input4Save input) throws IOException {
        FfeLuceneUtil.add(input.getEmbeds(), input.getImgUrl(), input.getId(), input.getGroupId());
    }

    /**
     * 根据输入对象中的ID删除对应的文档。
     *
     * @param input 包含要删除的文档ID的输入对象。
     * @throws IOException 如果在删除过程中发生I/O错误，则抛出此异常。
     */
    public void delete(Input4Del input) throws IOException {
        FfeLuceneUtil.delete(input.getId());
    }

    /**
     * 根据输入的人脸图像进行搜索
     *
     * @param input 包含图像URL等信息的输入对象
     * @return 搜索到的人脸图像对象，若未搜索到则返回null
     * @throws IOException 如果在读取图像或处理过程中发生I/O异常
     */
    public List<FaceInfo4Search> findMostSimilarFace(Input4Search input) throws IOException {
        Mat mat = ImageUtil.urlToMat(input.getImgUrl());
        FaceImage faceImage = getFaceInfos(mat);
        List<FaceInfo> faceInfos = new ArrayList<>();
        for (FaceInfo faceInfo : faceImage.getFaceInfos()) {
            // 人脸质量过滤
            if (faceInfo.getScore() > input.getFaceScoreThreshold()) {
                faceInfos.add(faceInfo);
            }
        }
        if (!faceInfos.isEmpty()) {
            // 执行搜索
            return FfeLuceneUtil.searchTop(faceInfos.get(0).getEmbedding().getEmbeds(), input.getGroupId(), input.getConfidenceThreshold(), 1);
        } else {
            throw new RuntimeException("no face found in image");
        }

    }

    /**
     * 根据输入参数获取人脸信息
     *
     * @param input 包含图像URL的输入参数对象
     * @return 包含人脸信息的FaceImage对象
     * @throws IOException 如果在读取图像文件时发生IO异常
     */
    public FaceImage computeFaceVector(InputWithUrl input) throws IOException {
        Mat mat = ImageUtil.urlToMat(input.getImgUrl());
        return getFaceInfos(mat);
    }

    /**
     * 从输入的图像中提取人脸信息列表
     * 该方法使用OpenCV的Mat对象作为输入，并调用人脸特征提取器来提取图像中的人脸信息。
     *
     * @param mat OpenCV的Mat对象，表示输入的图像
     * @return 包含提取到的人脸信息的FaceImage对象
     */
    private FaceImage getFaceInfos(Mat mat) {
        // 提取人脸特征
        long start_time = System.currentTimeMillis();
        Map<String, Object> params = Map.of(InsightScrfdFaceDetection.scrfdFaceNeedCheckFaceAngleParamKey, true);
        FaceImage faceImage = extractor.extract(ImageMat.fromCVMat(mat), params);
        log.info("extract : Cost time：{} ms.", (System.currentTimeMillis() - start_time));
        // 这里强制把图片字段置空，不然返回数据太大
        faceImage.setImageBase64(null);
        for (FaceInfo faceInfo : faceImage.getFaceInfos()) {
            faceInfo.getEmbedding().setImage(null);
        }

        return faceImage;
    }


    /**
     * 比较两张图片中的人脸相似度。
     *
     * @param input 包含两张图片URL的对象
     * @return 返回两张图片的人脸相似度，范围在0到1之间，1表示完全相同
     * @throws IOException 如果图片URL无法访问或读取失败
     */
    public double calculateSimilarity(Input4Compare input) throws IOException {
        Mat mat = ImageUtil.urlToMat(input.getImgUrl());
        float[] embeds = getFaceInfos(mat).getFaceInfos().get(0).getEmbedding().getEmbeds();

        Mat mat2 = ImageUtil.urlToMat(input.getImgUrl2());
        float[] embeds2 = getFaceInfos(mat2).getFaceInfos().get(0).getEmbedding().getEmbeds();

        return VectorUtil.calculateCosineSimilarity(VectorUtil.normalizeVector(embeds), VectorUtil.normalizeVector(embeds2));

    }

    /**
     * 根据输入图片等信息，做搜索。
     * 能搜到就返回搜到的值
     * 搜不到就把这张图片入库作为封面
     *
     * @param input 输入参数，包含图片URL、分组ID和置信度阈值
     * @return 返回人脸图像对象，如果未找到匹配的人脸则返回null
     * @throws IOException 如果读取图片时出现IO异常，则抛出此异常
     */
    public FaceInfo4SearchAdd findSave(Input4Search input) throws IOException {

        List<FaceInfo4Add> addList = new ArrayList<>();
        List<FaceInfo4Search> searchList = new ArrayList<>();

        // 拿到人脸特征
        Mat mat = ImageUtil.urlToMat(input.getImgUrl());
        FaceImage faceImage = getFaceInfos(mat);

        for (FaceInfo face : faceImage.getFaceInfos()) {
            // 对检测出来的每一个人脸都进行质量判断和搜索操作
            if (face.getScore() > input.getFaceScoreThreshold()) {
                List<FaceInfo4Search> search = FfeLuceneUtil.searchTop(face.getEmbedding().getEmbeds(), input.getGroupId(), input.getConfidenceThreshold(), 1);
                if (!search.isEmpty()) {
                    searchList.addAll(search);
                } else {
                    // 如果这个人脸在库中没有找到就需要入库
                    FfeLuceneUtil.add(face.getEmbedding().getEmbeds(), input.getImgUrl(), face.getId(), input.getGroupId());
                    addList.add(new FaceInfo4Add(face));
                }
            }
        }

        return new FaceInfo4SearchAdd(addList, searchList);
    }


}