package com.yuqiangdede.tbir.service;

import ai.onnxruntime.OrtException;
import com.yuqiangdede.common.dto.output.Box;
import com.yuqiangdede.common.util.ImageUtil;
import com.yuqiangdede.tbir.config.Constant;
import com.yuqiangdede.tbir.dto.AugmentedImage;
import com.yuqiangdede.tbir.dto.ImageEmbedding;
import com.yuqiangdede.tbir.dto.LuceHit;
import com.yuqiangdede.tbir.dto.input.SaveImageRequest;
import com.yuqiangdede.tbir.dto.input.DeleteImageRequest;
import com.yuqiangdede.tbir.dto.output.HitImage;
import com.yuqiangdede.tbir.dto.output.ImageSaveResult;
import com.yuqiangdede.tbir.dto.output.SearchResult;
import com.yuqiangdede.tbir.util.*;
import com.yuqiangdede.yolo.service.ImgAnalysisService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

import static com.yuqiangdede.tbir.config.Constant.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class TbirService {
    private final ClipEmbedder clipEmbedder;
    private final PromptExpand promptExpand;
    private final ImgAnalysisService imgAnalysisService;

    static {
        try {
            TbirVectorStoreUtil.init(Constant.LUCENE_PATH, Constant.VECTOR_PERSISTENCE_ENABLED);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 娣囨繂鐡ㄩ崶鍓у
     * 1閵嗕礁顕崶鍓у鏉╂稖顢戦惄顔界垼濡偓濞?瀵版鍩屽Λ鈧ù瀣攱
     * 2閵嗕礁顕Λ鈧ù瀣攱鏉╂稖顢戦幍鈺佺潔濡偓濞村顢嬮崠鍝勭厵 瀵版鍩岀€涙劕娴楢
     * 3閵嗕礁顕€涙劕娴樻潻娑滎攽婢舵俺顫嬬憴鎺戭杻瀵搫顦╅悶鍡礄缂傗晜鏂侀妴浣镐焊缁夋眹鈧焦妫嗘潪顒傜搼閿?瀵版鍩岄弴鏉戭樋閻ㄥ嫬鐡欓崶缍?
     * 4閵嗕礁顕崢鐔锋禈閸滃苯鐡欓崶缍涙潻娑滎攽閸氭垿鍣洪崠?
     * 5閵嗕礁顦垮Ο鈥崇€烽摶宥呮値(閺嗗倹妞傛稉宥呬粵)
     * 6閵嗕焦瀵旀稊鍛閸掔櫦ucene缁便垹绱╂惔鎿勭礉楠炴湹绗栧铏圭彌閸氭垿鍣洪崪灞芥禈閻楀洨娈戦弰鐘茬殸閸忓磭閮撮敍宀勬付鐟曚礁鐡ㄩ崒銊ф畱閺佺増宓侀張澶涚礄閸忓疇浠堟稉璇叉禈閻ㄥ埇d閵嗕礁鎮滈柌蹇嬧偓浣哥摍閸ュ墽娈慴ox閸ф劖鐖ｉ妴涔礶ta娣団剝浼呴妴浣规闂傚瓨鍩戦妴浣烘磧閹貉呭仯閵嗕礁鍨庣紒鍕剁礆
     * 7閵嗕浇绻戦崶鐐叉禈閻楀檮d閿涘牆顩ч弸婊勬箒鏉堟挸鍙嗙亸杈╂暏鏉堟挸鍙嗛惃鍒琩閿涘苯顩ч弸婊勭梾鏉堟挸鍙嗙亸杈殰閸斻劎鏁撻幋鎭id鏉╂柨娲栭敍?
     *
     * @param input 鏉堟挸鍙嗛崶鍓у閿涘苯鍙挎担鎾冲棘閺?
     *              閻╊喗鐖ｅΛ鈧ù瀣祲閸忕绱皌hreshold 缂冾喕淇婃惔锕傛閸婄》绱眛ypes 濡偓濞村娈戠猾璇茬€?
     *              閺嶇绺鹃崣鍌涙殶閿涙mgUrl 閸ュ墽澧杣rl閿涙铂mgId 閸ュ墽澧杋d閿涘本鐥呮潏鎾冲弳鐏忚精鍤滈崝銊ф晸閹存仴uid
     *              閸忔湹绮崣鍌涙殶閿涙瓭ameraId 閻╂垶甯堕悙?閺€顖涘瘮濡偓缁鳖澁绱眊roupId 閸ュ墽澧栭崚鍡欑矋 閺€顖涘瘮濡偓缁鳖澁绱眒eta 閸忔湹绮穱鈩冧紖 娑撳秵鏁幐浣诡梾缁鳖澁绱濋崣顏囧厴閺屻儴顕楅惃鍕閸婃瑨绻戦崶?
     */
    public ImageSaveResult saveImg(SaveImageRequest input) throws IOException, OrtException {
        // 0閵嗕浇骞忛崣鏍ф禈閻楀浄绱濋悽鐔稿灇imgId
        BufferedImage image = ImageUtil.urlToImage(input.getImgUrl());
        String imgId = input.getImgId() == null ? UUID.randomUUID().toString() : input.getImgId();

        
        long startTime = System.currentTimeMillis();
        List<Box> boxes = new ArrayList<>();
        if (OPEN_DETECT) {
            List<Box> boxs = new ArrayList<>();
            if (DETECT_TYPES.contains("yolo")) {
                collectDetections(boxs, () -> imgAnalysisService.detectArea(input));
            }
            if (DETECT_TYPES.contains("sam")) {
                collectDetections(boxs, () -> imgAnalysisService.sam(input));
            }
            for (Box box : boxs) {
                // 婵″倹鐏夐惄顔界垼閸︺劍瀵氱€规俺瀵栭崶鏉戝敶閹靛秴浠涢崥搴ｇ敾閻ㄥ嫬鎮滈柌蹇撳
                if (box.isValid(MIN_SIZE, MAX_SIZE)) {
                    boxes.add(box);
                }
            }
        }
        log.info("SaveImg detectArea: Cost time:{} ms.", (System.currentTimeMillis() - startTime));

        // 2閵嗕礁顕Λ鈧ù瀣攱鏉╂稖顢戦幍鈺佺潔
        // 3閵嗕礁顕€涙劕娴樻潻娑滎攽婢舵俺顫嬬憴鎺戭杻瀵搫顦╅悶鍡礄缂傗晜鏂侀妴浣镐焊缁夋眹鈧焦妫嗘潪顒傜搼閿?
        startTime = System.currentTimeMillis();
        List<AugmentedImage> subImgs = ImageCropAndAugmentUtil.cropAndAugment(image, boxes);
        // 4閵嗕礁顕崢鐔锋禈閸滃苯鐡欓崶缍涙潻娑滎攽閸氭垿鍣洪崠?
        List<ImageEmbedding> vectors = vectorize(image, subImgs);
        log.info("SaveImg ImageCrop vectorized: Cost time:{} ms.", (System.currentTimeMillis() - startTime));

        // 5閵嗕礁顦垮Ο鈥崇€烽摶宥呮値(閺嗗倹妞傛稉宥呬粵)
        // 6閵嗕焦瀵旀稊鍛閸掔櫦ucene缁便垹绱╂惔鎿勭礉楠炴湹绗栧铏圭彌閸氭垿鍣洪崪灞芥禈閻楀洨娈戦弰鐘茬殸閸忓磭閮?
        startTime = System.currentTimeMillis();
        persistToLucene(imgId, vectors, input);
        log.info("SaveImg persistToLucene: Cost time:{} ms.", (System.currentTimeMillis() - startTime));

        log.info("imgId閿涙}. boxes size:{}. subImgs size:{}. vectors size:{}", imgId, boxes.size(), subImgs.size(), vectors.size());
        // 7閵嗕浇绻戦崶鐐叉禈閻楀檮d
        return new ImageSaveResult(imgId);
    }

    /**
     * Persist every embedding that belongs to the given image into the Lucene vector index.
     * Each embedding is stored together with the logical image identifier and request metadata
     * so that subsequent vector searches can recover the original context.
     */
    private void persistToLucene(String imgId, List<ImageEmbedding> vectors, SaveImageRequest input) {
        for (ImageEmbedding emb : vectors) {
            TbirVectorStoreUtil.add(imgId, emb, input);
        }
    }

    /**
     * Execute a detection supplier and merge the resulting boxes into the accumulator, while
     * surfacing checked exceptions that the caller needs to handle (IO or inference errors).
     */
    private void collectDetections(List<Box> accumulator, Callable<List<Box>> supplier) throws IOException, OrtException {
        try {
            List<Box> detected = supplier.call();
            if (detected != null) {
                accumulator.addAll(detected);
            }
        } catch (IOException | OrtException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Detection execution failed", e);
        }
    }

    /**
     * 鐎电懓娴橀悧鍥箻鐞涘苯鎮滈柌蹇撳, subImgs 鐟曚礁鎷癰oxes娑撯偓娑撯偓鐎电懓绨?
     *
     * @param image   娑撹娴?
     * @param subImgs 鐎涙劕娴橀崚妤勩€?
     * @return 閸氭垿鍣洪崚妤勩€?
     */
    private List<ImageEmbedding> vectorize(BufferedImage image, List<AugmentedImage> subImgs) throws OrtException {
        List<ImageEmbedding> result = new ArrayList<>();

        // 1. 閸氭垿鍣洪崠鏍﹀瘜閸ユ拝绱欓弫鏉戞禈缁狙勬偝缁鳖澁绱?
        float[] mainVector = clipEmbedder.embedImage(image);  // 娴ｇ姴鍑￠張澶屾畱濡€崇€风亸浣筋棅閸?
        result.add(new ImageEmbedding(null, null, "full-image", mainVector, true));

        // 2. 閸氭垿鍣洪崠鏍х摍閸?
        for (AugmentedImage aug : subImgs) {
            float[] vector = clipEmbedder.embedImage(aug.getImage()); // 鐎涙劕娴橀崥鎴﹀櫤閸?
            result.add(new ImageEmbedding(
                    aug.getOriginalBox(),
                    aug.getCroppedBox(),
                    aug.getAugmentationType(),
                    vector,
                    false
            ));
        }

        return result;
    }

    /**
     * 閸掔娀娅庨崶鍓у閿涘矁顩﹂幎濠傚斧閸ユ儳鎷扮€涙劕娴橀崷銊ユ倻闁插繐绨辨稉顓犳畱閺佺増宓侀柈鑺ョ閻炲棙甯€
     *
     * @param input 閸ュ墽澧栭惃鍕偍瀵?
     */
    public void deleteImg(DeleteImageRequest input) {
        //TODO
    }

    /**
     * 閻劋绨弬鍥ㄦ偝閸ュ墽娈戦幖婊呭偍鏉╁洨鈻?
     * 鏉堟挸鍙嗛弰顖涙偝缁便垺鏋冮張顑锯偓浣烘磧閹貉呭仯id閵嗕礁鍨庣紒鍒琩閿涘opN閿涘牆绗囬張娑欐偝缁便垹鍩岄惃鍕瘜閸ュ墽娈戦弫浼村櫤閿?
     * 鏉堟挸鍤弰顖氭禈閻楀洤鍨悰顭掔礉濮ｅ繋閲滈崶鍓у鐟曚礁瀵橀崥顐窗娑撹娴榠d閵嗕椒瀵岄崶绶恟l閵嗕礁鐡欓崶鎯ь嚠鎼存梻娈戝Λ鈧ù瀣攱閿涘牆顩ч弸婊勬箒閻ㄥ嫯鐦介敍澶堚偓浣哥摍閸ュ墽娈戠純顔讳繆鎼达负鈧椒瀵岄崶鍓ф畱meta娣団剝浼呴妴浣峰瘜閸ュ墽娈戦惄鎴炲付閻愮d閸滃苯鍨庣紒鍒琩娣団剝浼?
     * 1閵嗕礁顦跨憴鎺戝閹兼粎鍌ㄩ崗鎶芥暛鐎涙澧跨仦?瀵版鍩屾径姘嚋閹兼粎鍌ㄩ崗鎶芥暛鐎涙绱濋崑鍥啎娑撶娑?
     * 2閵嗕礁顕妤€鍩岄惃鍕槨娑擃亝鎮崇槐銏犲彠闁款喖鐡ч柈钘変粵閸氭垿鍣洪崠鏍ь槱閻炲棴绱濋悞璺烘倵鐎佃鐦℃稉顏勬倻闁插繘鍏橀幍褑顢?.閸氭垿鍣烘惔鎾存偝缁?
     * 3閵嗕礁鎮滈柌蹇撶氨閹兼粎鍌ㄩ敍鍫熸偝缁便垺娼禒璁圭窗閻╂垶甯堕悙绛癲閵嗕礁鍨庣紒鍒琩閵嗕焦鎮崇槐銏㈡祲娴肩厧瀹抽梼鍫濃偓纭风礉TopN閿涘本鎮崇槐銏℃瀮閺堫剙鎮滈柌蹇撳閿涘绱濋幏鍨煂閸栧綊鍘ら惃鍕摍閸ョ偓鍨ㄩ懓鍛瘜閸?
     * 4閵嗕礁顕禍搴㈡偝缁便垻绮ㄩ弸婊嗙箻鐞涘苯鎮庨獮璁圭礄闂団偓鐟曚椒绔撮崗鍗炵繁閸戠opN娑擃亞绮ㄩ弸婊愮礉鐎圭偤妾幖婊呭偍鐠囧秵澧跨仦鏇＄箖閿涘本娓剁紒鍫濈杽闂勫懏婀乀opN * X娑擃亞绮ㄩ弸婊愮礉闂団偓鐟曚浇绻樼悰宀勨偓鏄忕帆閸氬牆鑻熼敍灞藉絿閻╅晲鎶€鎼达附娓舵妯兼畱娑撹娴橀敍?
     * 閸氬牆鑻熼柅鏄忕帆閿涙碍濡搁幍鈧張澶屾畱閹兼粎鍌ㄧ紒鎾寸亯閻ㄥ嫪瀵岄崶楣冨厴閹峰灝鍤弶銉礉閻掕泛鎮楅幐澶屽弾閻╅晲鎶€鎼达附甯撴惔蹇ョ礉閸欐牕绶遍張鈧崜宥夋桨閻ㄥ嚲opN娑?
     * 5閵嗕浇绻戦崶鐐插爱闁板秴鍩岄惃鍕瘜閸ユ儳鍨悰?
     */
    public SearchResult searchByText(String query, String cameraId, String groupId, Integer topN) {

        
        long startTime = System.currentTimeMillis();
        List<String> expandedPrompts = promptExpand.expand(query);
        while (expandedPrompts.size() < KEY_NUM) {
            expandedPrompts = promptExpand.expand(query);
        }
        int index = 1;
        for (String prompt : expandedPrompts) {
            log.info("{}: {}", index++, prompt);
        }
        log.info("searchByText PromptExpand chat: Cost time:{} ms.", (System.currentTimeMillis() - startTime));

        // 2閵嗕礁顕妤€鍩岄惃鍕槨娑擃亝鎮崇槐銏犲彠闁款喖鐡ч柈钘変粵閸氭垿鍣洪崠鏍ь槱閻?
        startTime = System.currentTimeMillis();
        List<float[]> vectors = clipEmbedder.embedTexts(expandedPrompts);
        log.info("searchByText embedTexts: Cost time:{} ms.", (System.currentTimeMillis() - startTime));

        // 3閵嗕礁顕稉濠囨桨瀵版鍩岄惃鍕槨娑擃亜鎮滈柌蹇涘厴閸嬫艾鎮滈柌蹇撶氨閹兼粎鍌?
        startTime = System.currentTimeMillis();
        List<LuceHit> allHits = new ArrayList<>();
        for (float[] vec : vectors) {
            List<LuceHit> hits = TbirVectorStoreUtil.searchByVector(vec, cameraId, groupId, topN);
            allHits.addAll(hits);
        }
        log.info("searchByText searchByVector: Cost time:{} ms.", (System.currentTimeMillis() - startTime));
        // 4閵嗕礁鎮庨獮鑸垫偝缁便垻绮ㄩ弸?
        List<HitImage> finalList = getFinalList(topN, allHits);

        // 5閵嗕浇绻戦崶鐐插爱闁板秴鍩岄惃鍕瘜閸ユ儳鍨悰?
        return new SearchResult(finalList);
    }

    /**
     * 娴犲海绮扮€规氨娈慙uceHit閸掓銆冩稉顓熷絹閸欐牕鑻熸潻鏂挎礀閸栧懎鎯堥崜宄祇pN娑擃亝娓舵妯虹繁閸掑棛娈慔itImage閸掓銆冮妴?
     *
     * @param topN    闂団偓鐟曚浇绻戦崶鐐垫畱閺堚偓妤傛ê绶遍崚鍜筰tImage閻ㄥ嫭鏆熼柌?
     * @param allHits 閸樼喎顫愰惃鍑﹗ceHit閸掓銆?
     * @return 閸栧懎鎯堥崜宄祇pN娑擃亝娓舵妯虹繁閸掑棛娈慔itImage閸掓銆?
     */
    private static List<HitImage> getFinalList(Integer topN, List<LuceHit> allHits) {
        Map<String, HitImage> hitMap = new HashMap<>();

        for (LuceHit hit : allHits) {
            String imageId = hit.getImageId();

            // 婵″倹鐏夋潻妯荤梾鐠佹澘缍嶆潻娆庨嚋娑撹娴橀敍灞藉帥閸掓稑缂?HitImage
            if (!hitMap.containsKey(imageId)) {
                HitImage img = new HitImage();
                img.setImageId(imageId);
                img.setImageUrl(hit.getImageUrl());
                img.setCameraId(hit.getCameraId());
                img.setGroupId(hit.getGroupId());
                img.setMeta(hit.getMeta());
                img.setMatchedBoxes(new ArrayList<>());
                img.setScore(hit.getScore()); // 閸掓繂顫愰崠鏍у瀻閺?
                hitMap.put(imageId, img);
            }

            // 缂佈呯敾婢跺嫮鎮?
            HitImage img = hitMap.get(imageId);

            // 閵嗘劕鍙ч柨顔衡偓鎴烆梾閺屻儱缍嬮崜?hit.box 閺勵垰鎯佸鑼病閸?matchedBoxes 闁插矂娼?
            if (hit.getBox() != null) {
                boolean exists = false;
                for (Box existingBox : img.getMatchedBoxes()) {
                    if (isSameBox(existingBox, hit.getBox())) {
                        exists = true;
                        break;
                    }
                }

                if (!exists) {
                    img.getMatchedBoxes().add(hit.getBox());
                }
            }


            // 閺囧瓨鏌婂妤€鍨庨敍姘絿閺堚偓婢堆冪繁閸?
            img.setScore(Math.max(img.getScore(), hit.getScore()));
        }

        return hitMap.values().stream()
                .sorted(Comparator.comparingDouble(HitImage::getScore).reversed())
                .limit(topN)
                .collect(Collectors.toList());
    }


    private static boolean isSameBox(Box b1, Box b2) {
        final float EPSILON = 1e-4f; // 鐎圭懓妯?
        return Math.abs(b1.getX1() - b2.getX1()) < EPSILON &&
                Math.abs(b1.getY1() - b2.getY1()) < EPSILON &&
                Math.abs(b1.getX2() - b2.getX2()) < EPSILON &&
                Math.abs(b1.getY2() - b2.getY2()) < EPSILON;
    }


    /**
     * Execute a text-based search and render the matched bounding boxes onto their source images.
     * The in-memory `BufferedImage` list can be streamed directly to the client.
     */
    public List<BufferedImage> searchByTextI(String query, String cameraId, String groupId, Integer topN) throws IOException {
        List<BufferedImage> images = new ArrayList<>();
        SearchResult result = searchByText(query, cameraId, groupId, topN);
        List<HitImage> hitImages = result.getResults();
        for (HitImage hit : hitImages) {
            BufferedImage image = ImageUtil.urlToImage(hit.getImageUrl());
            ImageUtil.drawImageWithBox(image, hit.getMatchedBoxes());
            images.add(image);
        }
        return images;
    }


    /**
     * Perform image-to-image retrieval by embedding the probe image and querying Lucene for the
     * closest stored vectors. The result retains metadata and matched boxes for downstream use.
     */
    public SearchResult imgSearch(BufferedImage bufferedImage, Integer topN) throws OrtException {
        
        long startTime = System.currentTimeMillis();
        float[] embedded = clipEmbedder.embedImage(bufferedImage);
        log.info("imgSearch embedImage: Cost time:{} ms.", (System.currentTimeMillis() - startTime));

        startTime = System.currentTimeMillis();
        List<LuceHit> luceHits = TbirVectorStoreUtil.searchByVector(embedded, "1", "1", topN);
        List<HitImage> finalList = getFinalList(topN, luceHits);
        log.info("imgSearch searchByVector: Cost time:{} ms.", (System.currentTimeMillis() - startTime));

        return new SearchResult(finalList);
    }


    /**
     * Fetch all Lucene hits that belong to the specified stored image identifier and adapt them
     * into the `SearchResult` DTO used by the API layer.
     */
    public SearchResult searchImg(String imgId) {
        List<LuceHit> hits = TbirVectorStoreUtil.searchById(imgId);
        List<HitImage> finalList = getFinalList(10, hits);
        return new SearchResult(finalList);
    }

    /**
     * Convenience variant of {@link #searchImg(String)} that draws matched boxes on the source
     * images so callers can return JPEG previews directly.
     */
    public List<BufferedImage> searchImgI(String imgId) throws IOException {
        List<BufferedImage> images = new ArrayList<>();
        SearchResult result = searchImg(imgId);
        List<HitImage> hitImages = result.getResults();
        for (HitImage hit : hitImages) {
            BufferedImage image = ImageUtil.urlToImage(hit.getImageUrl());
            ImageUtil.drawImageWithBox(image, hit.getMatchedBoxes());
            images.add(image);
        }
        return images;
    }
}


