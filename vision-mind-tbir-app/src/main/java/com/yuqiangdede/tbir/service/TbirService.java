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
            TbirLuceneUtil.init(Constant.LUCENE_PATH);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 保存图片
     * 1、对图片进行目标检测 得到检测框
     * 2、对检测框进行扩展检测框区域 得到子图A
     * 3、对子图进行多视角增强处理（缩放、偏移、旋转等） 得到更多的子图B
     * 4、对原图和子图B进行向量化
     * 5、多模型融合(暂时不做)
     * 6、持久化到lucene索引库，并且建立向量和图片的映射关系，需要存储的数据有（关联主图的id、向量、子图的box坐标、meta信息、时间戳、监控点、分组）
     * 7、返回图片id（如果有输入就用输入的id，如果没输入就自动生成uuid返回）
     *
     * @param input 输入图片，具体参数
     *              目标检测相关：threshold 置信度阈值；types 检测的类型
     *              核心参数：imgUrl 图片url；imgId 图片id，没输入就自动生成uuid
     *              其他参数：cameraId 监控点 支持检索；groupId 图片分组 支持检索；meta 其他信息 不支持检索，只能查询的时候返回
     */
    public ImageSaveResult saveImg(SaveImageRequest input) throws IOException, OrtException {
        // 0、获取图片，生成imgId
        BufferedImage image = ImageUtil.urlToImage(input.getImgUrl());
        String imgId = input.getImgId() == null ? UUID.randomUUID().toString() : input.getImgId();

        // 1、对图片进行目标检测
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
                // 如果目标在指定范围内才做后续的向量化
                if (box.isValid(MIN_SIZE, MAX_SIZE)) {
                    boxes.add(box);
                }
            }
        }
        log.info("SaveImg detectArea: Cost time:{} ms.", (System.currentTimeMillis() - startTime));

        // 2、对检测框进行扩展
        // 3、对子图进行多视角增强处理（缩放、偏移、旋转等）
        startTime = System.currentTimeMillis();
        List<AugmentedImage> subImgs = ImageCropAndAugmentUtil.cropAndAugment(image, boxes);
        // 4、对原图和子图B进行向量化
        List<ImageEmbedding> vectors = vectorize(image, subImgs);
        log.info("SaveImg ImageCrop vectorized: Cost time:{} ms.", (System.currentTimeMillis() - startTime));

        // 5、多模型融合(暂时不做)
        // 6、持久化到lucene索引库，并且建立向量和图片的映射关系
        startTime = System.currentTimeMillis();
        persistToLucene(imgId, vectors, input);
        log.info("SaveImg persistToLucene: Cost time:{} ms.", (System.currentTimeMillis() - startTime));

        log.info("imgId：{}. boxes size:{}. subImgs size:{}. vectors size:{}", imgId, boxes.size(), subImgs.size(), vectors.size());
        // 7、返回图片id
        return new ImageSaveResult(imgId);
    }

    /**
     * 将图像向量持久化到Lucene索引中。
     *
     * @param imgId   图像的唯一标识符
     * @param vectors 图像嵌入向量列表
     * @param input   保存图像请求对象
     */
    private void persistToLucene(String imgId, List<ImageEmbedding> vectors, SaveImageRequest input) {
        for (ImageEmbedding emb : vectors) {
            TbirLuceneUtil.add(imgId, emb, input);
        }
    }

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
     * 对图片进行向量化, subImgs 要和boxes一一对应
     *
     * @param image   主图
     * @param subImgs 子图列表
     * @return 向量列表
     */
    private List<ImageEmbedding> vectorize(BufferedImage image, List<AugmentedImage> subImgs) throws OrtException {
        List<ImageEmbedding> result = new ArrayList<>();

        // 1. 向量化主图（整图级搜索）
        float[] mainVector = clipEmbedder.embedImage(image);  // 你已有的模型封装器
        result.add(new ImageEmbedding(null, null, "full-image", mainVector, true));

        // 2. 向量化子图
        for (AugmentedImage aug : subImgs) {
            float[] vector = clipEmbedder.embedImage(aug.getImage()); // 子图向量化
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
     * 删除图片，要把原图和子图在向量库中的数据都清理掉
     *
     * @param input 图片的索引
     */
    public void deleteImg(DeleteImageRequest input) {
        //TODO
    }

    /**
     * 用于文搜图的搜索过程
     * 输入是搜索文本、监控点id、分组id，topN（希望搜索到的主图的数量）
     * 输出是图片列表，每个图片要包含：主图id、主图url、子图对应的检测框（如果有的话）、子图的置信度、主图的meta信息、主图的监控点id和分组id信息
     * 1、多角度搜索关键字扩展,得到多个搜索关键字，假设为X个
     * 2、对得到的每个搜索关键字都做向量化处理，然后对每个向量都执行3.向量库搜索
     * 3、向量库搜索（搜索条件：监控点id、分组id、搜索相似度阈值，TopN，搜索文本向量化），拿到匹配的子图或者主图
     * 4、对于搜索结果进行合并（需要一共得出TopN个结果，实际搜索词扩展过，最终实际有TopN * X个结果，需要进行逻辑合并，取相似度最高的主图）
     * 合并逻辑：把所有的搜索结果的主图都拿出来，然后按照相似度排序，取得最前面的TopN个
     * 5、返回匹配到的主图列表
     */
    public SearchResult searchByText(String query, String cameraId, String groupId, Integer topN) {

        // 1、多角度搜索关键字扩展
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

        // 2、对得到的每个搜索关键字都做向量化处理
        startTime = System.currentTimeMillis();
        List<float[]> vectors = clipEmbedder.embedTexts(expandedPrompts);
        log.info("searchByText embedTexts: Cost time:{} ms.", (System.currentTimeMillis() - startTime));

        // 3、对上面得到的每个向量都做向量库搜索
        startTime = System.currentTimeMillis();
        List<LuceHit> allHits = new ArrayList<>();
        for (float[] vec : vectors) {
            List<LuceHit> hits = TbirLuceneUtil.searchByVector(vec, cameraId, groupId, topN);
            allHits.addAll(hits);
        }
        log.info("searchByText searchByVector: Cost time:{} ms.", (System.currentTimeMillis() - startTime));
        // 4、合并搜索结果
        List<HitImage> finalList = getFinalList(topN, allHits);

        // 5、返回匹配到的主图列表
        return new SearchResult(finalList);
    }

    /**
     * 从给定的LuceHit列表中提取并返回包含前topN个最高得分的HitImage列表。
     *
     * @param topN    需要返回的最高得分HitImage的数量
     * @param allHits 原始的LuceHit列表
     * @return 包含前topN个最高得分的HitImage列表
     */
    private static List<HitImage> getFinalList(Integer topN, List<LuceHit> allHits) {
        Map<String, HitImage> hitMap = new HashMap<>();

        for (LuceHit hit : allHits) {
            String imageId = hit.getImageId();

            // 如果还没记录这个主图，先创建 HitImage
            if (!hitMap.containsKey(imageId)) {
                HitImage img = new HitImage();
                img.setImageId(imageId);
                img.setImageUrl(hit.getImageUrl());
                img.setCameraId(hit.getCameraId());
                img.setGroupId(hit.getGroupId());
                img.setMeta(hit.getMeta());
                img.setMatchedBoxes(new ArrayList<>());
                img.setScore(hit.getScore()); // 初始化分数
                hitMap.put(imageId, img);
            }

            // 继续处理
            HitImage img = hitMap.get(imageId);

            // 【关键】检查当前 hit.box 是否已经在 matchedBoxes 里面
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


            // 更新得分：取最大得分
            img.setScore(Math.max(img.getScore(), hit.getScore()));
        }

        return hitMap.values().stream()
                .sorted(Comparator.comparingDouble(HitImage::getScore).reversed())
                .limit(topN)
                .collect(Collectors.toList());
    }


    private static boolean isSameBox(Box b1, Box b2) {
        final float EPSILON = 1e-4f; // 容差
        return Math.abs(b1.getX1() - b2.getX1()) < EPSILON &&
                Math.abs(b1.getY1() - b2.getY1()) < EPSILON &&
                Math.abs(b1.getX2() - b2.getX2()) < EPSILON &&
                Math.abs(b1.getY2() - b2.getY2()) < EPSILON;
    }


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
     * 图搜图的实现
     * 1 首先将图片转为向量
     * 2 向量搜图
     */
    public SearchResult imgSearch(BufferedImage bufferedImage, Integer topN) throws OrtException {
        // 1 图片转向量
        long startTime = System.currentTimeMillis();
        float[] embedded = clipEmbedder.embedImage(bufferedImage);
        log.info("imgSearch embedImage: Cost time:{} ms.", (System.currentTimeMillis() - startTime));

        startTime = System.currentTimeMillis();
        List<LuceHit> luceHits = TbirLuceneUtil.searchByVector(embedded, "1", "1", topN);
        List<HitImage> finalList = getFinalList(topN, luceHits);
        log.info("imgSearch searchByVector: Cost time:{} ms.", (System.currentTimeMillis() - startTime));

        return new SearchResult(finalList);
    }


    public SearchResult searchImg(String imgId) {
        List<LuceHit> hits = TbirLuceneUtil.searchById(imgId);
        List<HitImage> finalList = getFinalList(10, hits);
        return new SearchResult(finalList);
    }

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
