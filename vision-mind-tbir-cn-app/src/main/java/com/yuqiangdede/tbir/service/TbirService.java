package com.yuqiangdede.tbir.service;

import ai.onnxruntime.OrtException;
import com.yuqiangdede.common.dto.output.Box;
import com.yuqiangdede.common.util.ImageUtil;
import com.yuqiangdede.common.util.VectorUtil;
import com.yuqiangdede.common.vector.ElasticsearchConfig;
import com.yuqiangdede.tbir.config.Constant;
import com.yuqiangdede.tbir.dto.AugmentedImage;
import com.yuqiangdede.tbir.dto.ImageEmbedding;
import com.yuqiangdede.tbir.dto.LuceHit;
import com.yuqiangdede.tbir.dto.input.SaveImageRequest;
import com.yuqiangdede.tbir.dto.input.DeleteImageRequest;
import com.yuqiangdede.tbir.dto.output.HitImage;
import com.yuqiangdede.tbir.dto.output.ImageSaveResult;
import com.yuqiangdede.tbir.dto.output.SearchResult;
import com.yuqiangdede.tbir.dto.output.SimilarityResult;
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
    private final ImgAnalysisService imgAnalysisService;

    static {
        try {
            ElasticsearchConfig esConfig = new ElasticsearchConfig(
                    Constant.ES_URIS,
                    Constant.ES_USERNAME,
                    Constant.ES_PASSWORD,
                    Constant.ES_API_KEY,
                    Constant.ES_TBIR_INDEX);
            TbirVectorStoreUtil.init(Constant.LUCENE_PATH, Constant.VECTOR_STORE_MODE, esConfig);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Stores an image and its derived crops in the vector store for later retrieval.
     * Optionally runs object detection, crops/augments the image, embeds every crop,
     * and persists all resulting vectors together with metadata.
     *
     * @param input request describing the source image, optional ids, detection config, and metadata
     * @return result containing the resolved image id
     */
    public ImageSaveResult saveImg(SaveImageRequest input) throws IOException, OrtException {
        List<String> urls = resolveImageUrls(input);
        if (urls.size() != 1) {
            throw new IllegalArgumentException("saveImg expects exactly one image; use saveImgs for batch");
        }
        String url = urls.get(0);
        SaveImageRequest request = buildRequestForUrl(input, url, input.getImgId());
        return saveOne(request);
    }

    public List<ImageSaveResult> saveImgs(SaveImageRequest input) throws IOException, OrtException {
        List<String> urls = resolveImageUrls(input);
        if (urls.isEmpty()) {
            throw new IllegalArgumentException("imgUrl/imgUrls must not be empty");
        }
        if (urls.size() > 1 && input.getImgId() != null && !input.getImgId().isBlank()) {
            throw new IllegalArgumentException("imgId must be empty when saving multiple images");
        }
        long batchStart = System.currentTimeMillis();
        List<ImageSaveResult> results = new ArrayList<>();
        for (String url : urls) {
            SaveImageRequest request = buildRequestForUrl(input, url, null);
            results.add(saveOne(request));
        }
        log.info("SaveImgs completed. images: {} cost: {} ms.", results.size(), (System.currentTimeMillis() - batchStart));
        return results;
    }

    private ImageSaveResult saveOne(SaveImageRequest input) throws IOException, OrtException {
        // Step 0: download the source image and decide which identifier to persist.
        BufferedImage image = ImageUtil.urlToImage(input.getImgUrl());
        String imgId = input.getImgId() == null ? UUID.randomUUID().toString() : input.getImgId();

        long totalStart = System.currentTimeMillis();
        long startTime = System.currentTimeMillis();
        List<Box> boxes = new ArrayList<>();
        long yoloCostMs = 0L;
        long samCostMs = 0L;
        if (OPEN_DETECT) {
            List<Box> detectedBoxes = new ArrayList<>();
            if (DETECT_TYPES.contains("yolo")) {
                long yoloStart = System.currentTimeMillis();
                collectDetections(detectedBoxes, () -> imgAnalysisService.detectArea(input));
                yoloCostMs = System.currentTimeMillis() - yoloStart;
            }
            if (DETECT_TYPES.contains("sam")) {
                long samStart = System.currentTimeMillis();
                collectDetections(detectedBoxes, () -> imgAnalysisService.sam(input));
                samCostMs = System.currentTimeMillis() - samStart;
            }
            for (Box box : detectedBoxes) {
                // Only keep boxes that fall within the configured size limits.
                if (box.isValid(MIN_SIZE, MAX_SIZE)) {
                    boxes.add(box);
                }
            }
        }
        long detectCostMs = System.currentTimeMillis() - startTime;
        log.info("SaveImg detect: yolo={} ms, sam={} ms, total={} ms, boxes={}", yoloCostMs, samCostMs, detectCostMs, boxes.size());

        // Step 1: generate crops/augmentations based on the detection boxes.
        startTime = System.currentTimeMillis();
        List<AugmentedImage> subImgs = ImageCropAndAugmentUtil.cropAndAugment(image, boxes);
        long cropCostMs = System.currentTimeMillis() - startTime;

        // Step 2: embed the full image and each crop.
        List<ImageEmbedding> vectors = vectorize(image, subImgs);
        long embedCostMs = System.currentTimeMillis() - startTime;
        log.info("SaveImg vectorize: crops={} vectors={} cost={} ms.", subImgs.size(), vectors.size(), (cropCostMs + embedCostMs));

        // Step 3: persist embeddings to the configured vector store.
        startTime = System.currentTimeMillis();
        persistToLucene(imgId, vectors, input);
        long persistCostMs = System.currentTimeMillis() - startTime;

        log.info("SaveImg stored: imgId={} vectors={} persistCost={} ms totalCost={} ms", imgId, vectors.size(), persistCostMs, (System.currentTimeMillis() - totalStart));
        // Step 4: return the identifier so the caller can reference it later.
        return new ImageSaveResult(imgId);
    }

    /**
     * Persist every embedding that belongs to the given image in the configured vector store.
     * Each embedding is stored with the logical image identifier and request metadata so that
     * subsequent searches can recover the original context.
     */
    private void persistToLucene(String imgId, List<ImageEmbedding> vectors, SaveImageRequest input) {
        for (ImageEmbedding emb : vectors) {
            TbirVectorStoreUtil.add(imgId, emb, input);
        }
    }

    private static List<String> resolveImageUrls(SaveImageRequest input) {
        if (input == null) {
            return Collections.emptyList();
        }
        if (input.getImgUrls() != null && !input.getImgUrls().isEmpty()) {
            List<String> cleaned = new ArrayList<>();
            for (String url : input.getImgUrls()) {
                if (url == null) {
                    continue;
                }
                String trimmed = url.trim();
                if (!trimmed.isEmpty()) {
                    cleaned.add(trimmed);
                }
            }
            return cleaned;
        }
        if (input.getImgUrl() == null || input.getImgUrl().trim().isEmpty()) {
            return Collections.emptyList();
        }
        return Collections.singletonList(input.getImgUrl().trim());
    }

    private static SaveImageRequest buildRequestForUrl(SaveImageRequest base, String url, String imgIdOverride) {
        SaveImageRequest req = new SaveImageRequest();
        req.setImgId(imgIdOverride);
        req.setCameraId(base.getCameraId());
        req.setGroupId(base.getGroupId());
        req.setMeta(base.getMeta());
        req.setImgUrl(url);
        req.setThreshold(base.getThreshold());
        req.setTypes(base.getTypes());
        req.setDetectionFrames(base.getDetectionFrames());
        req.setBlockingFrames(base.getBlockingFrames());
        return req;
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
     * Generates embeddings for the full image and the provided augmented crops.
     *
     * @param image   original image downloaded from the request
     * @param subImgs augmented or cropped variants derived from the original image
     * @return list of embeddings ready to be persisted
     */
    private List<ImageEmbedding> vectorize(BufferedImage image, List<AugmentedImage> subImgs) throws OrtException {
        List<ImageEmbedding> result = new ArrayList<>();

        // Embed the entire image as the main representation.
        float[] mainVector = clipEmbedder.embedImage(image);
        result.add(new ImageEmbedding(null, null, "full-image", mainVector, true));

        // Embed each augmented crop and attach its metadata.
        for (AugmentedImage aug : subImgs) {
            float[] vector = clipEmbedder.embedImage(aug.getImage());
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
     * Remove all vectors that belong to the supplied image id across the active vector store.
     *
     * @param input request containing the image identifier to delete
     */
    public void deleteImg(DeleteImageRequest input) {
        String imgId = input.getImgId();
        if (imgId == null || imgId.isBlank()) {
            throw new IllegalArgumentException("imgId must not be blank");
        }
        long startTime = System.currentTimeMillis();
        try {
            TbirVectorStoreUtil.delete(imgId);
        } catch (IOException e) {
            throw new RuntimeException("Failed to delete image " + imgId, e);
        }
        log.info("Deleted image {} in {} ms.", imgId, (System.currentTimeMillis() - startTime));
    }

    /**
     * Executes a text-to-image retrieval by expanding the query into prompts, embedding each prompt,
     * querying the vector store, and merging the hits into ranked images.
     *
     * @param query    natural language query to match against stored vectors
     * @param cameraId optional camera filter applied during vector search
     * @param groupId  optional group filter applied during vector search
     * @param topN     maximum number of images to return
     * @return aggregated search result
     */
    public SearchResult searchByText(String query, String cameraId, String groupId, Integer topN) {

        long startTime = System.currentTimeMillis();

        List<float[]> vectors = Collections.singletonList(clipEmbedder.embedText(query));
        log.info("searchByText embedTexts: Cost time:{} ms.", (System.currentTimeMillis() - startTime));

        // Step 3: query the vector store for every embedding.
        startTime = System.currentTimeMillis();
        List<LuceHit> allHits = new ArrayList<>();
        for (float[] vec : vectors) {
            List<LuceHit> hits = TbirVectorStoreUtil.searchByVector(vec, cameraId, groupId, topN);
            allHits.addAll(hits);
        }
        log.info("searchByText searchByVector: Cost time:{} ms.", (System.currentTimeMillis() - startTime));

        // Step 4: merge hits by image id and keep the highest scores.
        List<HitImage> finalList = getFinalList(topN, allHits);

        // Step 5: wrap the aggregated hits in the API response.
        return new SearchResult(finalList);
    }

    /**
     * Merge raw vector-store hits by image id and keep the highest scoring bounding boxes per image.
     *
     * @param topN    number of images to return
     * @param allHits raw hits returned by the vector store
     * @return ranked list of aggregated image hits
     */
    private static List<HitImage> getFinalList(Integer topN, List<LuceHit> allHits) {
        Map<String, HitImage> hitMap = new HashMap<>();

        for (LuceHit hit : allHits) {
            String imageId = hit.getImageId();

            // Create a new aggregated record the first time we encounter this image id.
            if (!hitMap.containsKey(imageId)) {
                HitImage img = new HitImage();
                img.setImageId(imageId);
                img.setImageUrl(hit.getImageUrl());
                img.setCameraId(hit.getCameraId());
                img.setGroupId(hit.getGroupId());
                img.setMeta(hit.getMeta());
                img.setMatchedBoxes(new ArrayList<>());
                img.setScore(hit.getScore());
                hitMap.put(imageId, img);
            }

            // Retrieve the aggregated record for this image.
            HitImage img = hitMap.get(imageId);

            // Add the box if we have not already recorded an equivalent one.
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

            // Track the highest score observed for this image.
            img.setScore(Math.max(img.getScore(), hit.getScore()));
        }

        return hitMap.values().stream()
                .sorted(Comparator.comparingDouble(HitImage::getScore).reversed())
                .limit(topN)
                .collect(Collectors.toList());
    }


    private static boolean isSameBox(Box b1, Box b2) {
        final float EPSILON = 1e-4f; // Tolerance for float comparisons when deduplicating boxes.
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

    public SimilarityResult similarityTextImage(String text, BufferedImage image) throws OrtException {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("text must not be blank");
        }
        if (image == null) {
            throw new IllegalArgumentException("image must not be null");
        }
        float[] textVec = clipEmbedder.embedText(text);
        float[] imageVec = clipEmbedder.embedImage(image);
        return new SimilarityResult(cosineSimilarity(textVec, imageVec));
    }

    public SimilarityResult similarityImageImage(BufferedImage image1, BufferedImage image2) throws OrtException {
        if (image1 == null || image2 == null) {
            throw new IllegalArgumentException("images must not be null");
        }
        float[] vec1 = clipEmbedder.embedImage(image1);
        float[] vec2 = clipEmbedder.embedImage(image2);
        return new SimilarityResult(cosineSimilarity(vec1, vec2));
    }

    private static double cosineSimilarity(float[] a, float[] b) {
        if (a.length != b.length) {
            throw new IllegalArgumentException("Vectors must be of the same length.");
        }
        float[] na = VectorUtil.normalizeVector(a);
        float[] nb = VectorUtil.normalizeVector(b);
        double dot = 0.0;
        for (int i = 0; i < na.length; i++) {
            dot += (double) na[i] * nb[i];
        }
        return dot;
    }
}


