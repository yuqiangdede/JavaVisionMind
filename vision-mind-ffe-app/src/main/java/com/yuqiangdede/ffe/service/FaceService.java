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
import com.yuqiangdede.ffe.util.FfeVectorStoreUtil;
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
        if (shouldSkipNativeLoad()) {
            log.debug("Skipping face feature extractor native initialization for tests.");
            extractor = null;
        } else {
            try {
                FaceDetection insightScrfdFaceDetection = new InsightScrfdFaceDetection(Constant.MODEL_SCRFD_PATH, 1);
                FaceKeyPoint insightCoordFaceKeyPoint = new InsightCoordFaceKeyPoint(Constant.MODEL_COORD_PATH, 1);
                FaceRecognition insightArcFaceRecognition = new InsightArcFaceRecognition(Constant.MODEL_ARC_PATH, 1);
                FaceAlignment simple106pFaceAlignment = new Simple106pFaceAlignment();
                FaceAttribute insightFaceAttribute = new InsightAttributeDetection(Constant.MODEL_ARR_PATH, 1);

                FfeVectorStoreUtil.init(Constant.LUCENE_PATH, Constant.VECTOR_PERSISTENCE_ENABLED);

                extractor = new FaceFeatureExtractorImpl(
                        insightScrfdFaceDetection, insightCoordFaceKeyPoint,
                        simple106pFaceAlignment, insightArcFaceRecognition, insightFaceAttribute);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static boolean shouldSkipNativeLoad() {
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


    /**
     * 娣诲姞杈撳叆鍥惧儚骞舵彁鍙栦汉鑴哥壒寰侊紝灏嗘彁鍙栧埌鐨勪汉鑴镐俊鎭叆搴?
     *
     * @param input 杈撳叆瀵硅薄锛屽寘鍚浘鍍廢RL绛変俊鎭?
     * @return 鍖呭惈鎻愬彇鍒扮殑浜鸿劯淇℃伅鐨凢aceImage瀵硅薄
     * @throws IOException 濡傛灉鍦ㄦ坊鍔犺繃绋嬩腑鍙戠敓寮傚父锛屽垯鎶涘嚭璇ュ紓甯?
     */
    public FaceImage computeAndSaveFaceVector(InputWithUrl input) throws IOException {
        Mat mat = ImageUtil.urlToMat(input.getImgUrl());
        FaceImage faceImage = getFaceInfos(mat);
        List<FaceInfo> fs = faceImage.getFaceInfos();
        List<FaceInfo> faceInfos = new ArrayList<>();
        // 鑻ユ湁澶氫釜浜鸿劯灏遍兘鍏ュ簱
        for (FaceInfo faceInfo : fs) {
            // 澶т簬璁剧疆鐨勯槇鍊肩殑浜鸿劯鎵嶅叆搴撳拰杩斿洖锛屽惁鍒欓兘涓㈡帀
            if (faceInfo.getScore() > input.getFaceScoreThreshold()) {
                // 娣诲姞鍒扮储寮曞簱
                FfeVectorStoreUtil.add(faceInfo.getEmbedding().getEmbeds(), input.getImgUrl(), faceInfo.getId(), input.getGroupId());
                faceInfos.add(faceInfo);
            }
        }
        faceImage.setFaceInfos(faceInfos);
        return faceImage;
    }

    public void saveFaceVector(Input4Save input) throws IOException {
        FfeVectorStoreUtil.add(input.getEmbeds(), input.getImgUrl(), input.getId(), input.getGroupId());
    }

    /**
     * 鏍规嵁杈撳叆瀵硅薄涓殑ID鍒犻櫎瀵瑰簲鐨勬枃妗ｃ€?
     *
     * @param input 鍖呭惈瑕佸垹闄ょ殑鏂囨。ID鐨勮緭鍏ュ璞°€?
     * @throws IOException 濡傛灉鍦ㄥ垹闄よ繃绋嬩腑鍙戠敓I/O閿欒锛屽垯鎶涘嚭姝ゅ紓甯搞€?
     */
    public void delete(Input4Del input) throws IOException {
        FfeVectorStoreUtil.delete(input.getId());
    }

    /**
     * 鏍规嵁杈撳叆鐨勪汉鑴稿浘鍍忚繘琛屾悳绱?
     *
     * @param input 鍖呭惈鍥惧儚URL绛変俊鎭殑杈撳叆瀵硅薄
     * @return 鎼滅储鍒扮殑浜鸿劯鍥惧儚瀵硅薄锛岃嫢鏈悳绱㈠埌鍒欒繑鍥瀗ull
     * @throws IOException 濡傛灉鍦ㄨ鍙栧浘鍍忔垨澶勭悊杩囩▼涓彂鐢烮/O寮傚父
     */
    public List<FaceInfo4Search> findMostSimilarFace(Input4Search input) throws IOException {
        Mat mat = ImageUtil.urlToMat(input.getImgUrl());
        FaceImage faceImage = getFaceInfos(mat);
        List<FaceInfo> faceInfos = new ArrayList<>();
        for (FaceInfo faceInfo : faceImage.getFaceInfos()) {
            // 浜鸿劯璐ㄩ噺杩囨护
            if (faceInfo.getScore() > input.getFaceScoreThreshold()) {
                faceInfos.add(faceInfo);
            }
        }
        if (!faceInfos.isEmpty()) {
            // 鎵ц鎼滅储
            return FfeVectorStoreUtil.searchTop(faceInfos.get(0).getEmbedding().getEmbeds(), input.getGroupId(), input.getConfidenceThreshold(), 1);
        } else {
            throw new RuntimeException("no face found in image");
        }

    }

    /**
     * 鏍规嵁杈撳叆鍙傛暟鑾峰彇浜鸿劯淇℃伅
     *
     * @param input 鍖呭惈鍥惧儚URL鐨勮緭鍏ュ弬鏁板璞?
     * @return 鍖呭惈浜鸿劯淇℃伅鐨凢aceImage瀵硅薄
     * @throws IOException 濡傛灉鍦ㄨ鍙栧浘鍍忔枃浠舵椂鍙戠敓IO寮傚父
     */
    public FaceImage computeFaceVector(InputWithUrl input) throws IOException {
        Mat mat = ImageUtil.urlToMat(input.getImgUrl());
        return getFaceInfos(mat);
    }

    /**
     * 浠庤緭鍏ョ殑鍥惧儚涓彁鍙栦汉鑴镐俊鎭垪琛?
     * 璇ユ柟娉曚娇鐢∣penCV鐨凪at瀵硅薄浣滀负杈撳叆锛屽苟璋冪敤浜鸿劯鐗瑰緛鎻愬彇鍣ㄦ潵鎻愬彇鍥惧儚涓殑浜鸿劯淇℃伅銆?
     *
     * @param mat OpenCV鐨凪at瀵硅薄锛岃〃绀鸿緭鍏ョ殑鍥惧儚
     * @return 鍖呭惈鎻愬彇鍒扮殑浜鸿劯淇℃伅鐨凢aceImage瀵硅薄
     */
    private FaceImage getFaceInfos(Mat mat) {
        // 鎻愬彇浜鸿劯鐗瑰緛
        long start_time = System.currentTimeMillis();
        Map<String, Object> params = Map.of(InsightScrfdFaceDetection.scrfdFaceNeedCheckFaceAngleParamKey, true);
        FaceImage faceImage = extractor.extract(ImageMat.fromCVMat(mat), params);
        log.info("extract : Cost time锛歿} ms.", (System.currentTimeMillis() - start_time));
        // 杩欓噷寮哄埗鎶婂浘鐗囧瓧娈电疆绌猴紝涓嶇劧杩斿洖鏁版嵁澶ぇ
        faceImage.setImageBase64(null);
        for (FaceInfo faceInfo : faceImage.getFaceInfos()) {
            faceInfo.getEmbedding().setImage(null);
        }

        return faceImage;
    }


    /**
     * 姣旇緝涓ゅ紶鍥剧墖涓殑浜鸿劯鐩镐技搴︺€?
     *
     * @param input 鍖呭惈涓ゅ紶鍥剧墖URL鐨勫璞?
     * @return 杩斿洖涓ゅ紶鍥剧墖鐨勪汉鑴哥浉浼煎害锛岃寖鍥村湪0鍒?涔嬮棿锛?琛ㄧず瀹屽叏鐩稿悓
     * @throws IOException 濡傛灉鍥剧墖URL鏃犳硶璁块棶鎴栬鍙栧け璐?
     */
    public double calculateSimilarity(Input4Compare input) throws IOException {
        Mat mat = ImageUtil.urlToMat(input.getImgUrl());
        float[] embeds = getFaceInfos(mat).getFaceInfos().get(0).getEmbedding().getEmbeds();

        Mat mat2 = ImageUtil.urlToMat(input.getImgUrl2());
        float[] embeds2 = getFaceInfos(mat2).getFaceInfos().get(0).getEmbedding().getEmbeds();

        return VectorUtil.calculateCosineSimilarity(VectorUtil.normalizeVector(embeds), VectorUtil.normalizeVector(embeds2));

    }

    /**
     * 鏍规嵁杈撳叆鍥剧墖绛変俊鎭紝鍋氭悳绱€?
     * 鑳芥悳鍒板氨杩斿洖鎼滃埌鐨勫€?
     * 鎼滀笉鍒板氨鎶婅繖寮犲浘鐗囧叆搴撲綔涓哄皝闈?
     *
     * @param input 杈撳叆鍙傛暟锛屽寘鍚浘鐗嘦RL銆佸垎缁処D鍜岀疆淇″害闃堝€?
     * @return 杩斿洖浜鸿劯鍥惧儚瀵硅薄锛屽鏋滄湭鎵惧埌鍖归厤鐨勪汉鑴稿垯杩斿洖null
     * @throws IOException 濡傛灉璇诲彇鍥剧墖鏃跺嚭鐜癐O寮傚父锛屽垯鎶涘嚭姝ゅ紓甯?
     */
    public FaceInfo4SearchAdd findSave(Input4Search input) throws IOException {

        List<FaceInfo4Add> addList = new ArrayList<>();
        List<FaceInfo4Search> searchList = new ArrayList<>();

        // 鎷垮埌浜鸿劯鐗瑰緛
        Mat mat = ImageUtil.urlToMat(input.getImgUrl());
        FaceImage faceImage = getFaceInfos(mat);

        for (FaceInfo face : faceImage.getFaceInfos()) {
            // 瀵规娴嬪嚭鏉ョ殑姣忎竴涓汉鑴搁兘杩涜璐ㄩ噺鍒ゆ柇鍜屾悳绱㈡搷浣?
            if (face.getScore() > input.getFaceScoreThreshold()) {
                List<FaceInfo4Search> search = FfeVectorStoreUtil.searchTop(face.getEmbedding().getEmbeds(), input.getGroupId(), input.getConfidenceThreshold(), 1);
                if (!search.isEmpty()) {
                    searchList.addAll(search);
                } else {
                    // 濡傛灉杩欎釜浜鸿劯鍦ㄥ簱涓病鏈夋壘鍒板氨闇€瑕佸叆搴?
                    FfeVectorStoreUtil.add(face.getEmbedding().getEmbeds(), input.getImgUrl(), face.getId(), input.getGroupId());
                    addList.add(new FaceInfo4Add(face));
                }
            }
        }

        return new FaceInfo4SearchAdd(addList, searchList);
    }


}


