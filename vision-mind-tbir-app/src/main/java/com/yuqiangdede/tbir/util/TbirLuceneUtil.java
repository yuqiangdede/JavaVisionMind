package com.yuqiangdede.tbir.util;

import com.yuqiangdede.common.dto.output.Box;
import com.yuqiangdede.common.util.JsonUtils;
import com.yuqiangdede.tbir.dto.ImageEmbedding;
import com.yuqiangdede.tbir.dto.LuceHit;
import com.yuqiangdede.tbir.dto.input.SaveImageRequest;
import org.apache.lucene.document.*;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.*;
import org.apache.lucene.store.FSDirectory;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.yuqiangdede.tbir.config.Constant.OPEN_DETECT;

public class TbirLuceneUtil {
    private static SearcherManager searcherManager;
    private static IndexWriter writer;
    private static FSDirectory directory;
    private static IndexSearcher searcher;
    private static final String VECTOR_FIELD = "vector";


    /**
     * 初始化索引功能
     *
     * @throws Exception 如果初始化过程中出现错误，则抛出异常
     */
    public static void init(String indexPath) throws Exception {
        directory = FSDirectory.open(Paths.get(indexPath));

        // 创建 IndexWriter
        IndexWriterConfig config = new IndexWriterConfig();
        config.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);
        writer = new IndexWriter(directory, config);

        // 初始化 SearcherManager
        searcherManager = new SearcherManager(writer, new SearcherFactory());

        // 提交并刷新 SearcherManager
        writer.commit();
        searcherManager.maybeRefresh();

        searcher = searcherManager.acquire();
    }

    public static void close() throws Exception {
        // 关闭资源
        searcherManager.close();
        writer.close();
        directory.close();
    }

    /**
     * 将图像嵌入信息保存到 Lucene 索引中。
     *
     * @param imageId 图像的唯一标识符。
     * @param emb     图像嵌入对象，包含图像的向量表示和元数据。
     * @param input   包含图像附加信息的请求对象。
     * @throws RuntimeException 如果写入 Lucene 索引失败，将抛出运行时异常。
     */
    public static void add(String imageId, ImageEmbedding emb, SaveImageRequest input) {
        Document doc = new Document();
        // 向量字段
        doc.add(new KnnFloatVectorField("vector", emb.getVector()));
        // 图像主键 + 是否主图 +  图片url
        doc.add(new StringField("image_id", imageId, Field.Store.YES));
        doc.add(new StringField("main", emb.isMainImage() ? "1" : "0", Field.Store.YES));
        if (input.getImgUrl() != null) {
            doc.add(new StoredField("img_url", input.getImgUrl()));
        }
        // 坐标框（用于UI展示）
        Box sourceBox = emb.getSourceBox();
        if (sourceBox != null) {
            doc.add(new StoredField("box_x1", sourceBox.getX1()));
            doc.add(new StoredField("box_y1", sourceBox.getY1()));
            doc.add(new StoredField("box_x2", sourceBox.getX2()));
            doc.add(new StoredField("box_y2", sourceBox.getY2()));
        }
        // 可选业务字段
        if (input.getCameraId() != null) {
            doc.add(new StringField("camera_id", input.getCameraId(), Field.Store.YES));
        }
        if (input.getGroupId() != null) {
            doc.add(new StringField("group_id", input.getGroupId(), Field.Store.YES));
        }
        // meta（转成JSON方便展示）
        if (input.getMeta() != null && !input.getMeta().isEmpty()) {
            String metaJson = JsonUtils.map2Json(input.getMeta());
            doc.add(new StoredField("meta_json", metaJson));
        }
        // 时间戳
        long now = System.currentTimeMillis();
        doc.add(new LongPoint("timestamp", now));
        doc.add(new StoredField("timestamp", now));
        // 写入 Lucene
        try {
            writer.addDocument(doc);

            searcherManager.maybeRefresh();     // 刷新索引
            searcher = searcherManager.acquire(); // 更新最新的 IndexSearcher
            writer.commit();
        } catch (IOException e) {
            throw new RuntimeException("Lucene 写入失败", e);
        }
    }


    /**
     * 根据给定的ID删除对应的文档。
     *
     * @param id 需要删除的文档的ID。
     * @throws IOException 如果在删除文档的过程中发生I/O错误。
     */
    public static void delete(String id) throws IOException {
        writer.deleteDocuments(new Term("image_id", id));
        writer.commit();
        searcherManager.maybeRefresh();
    }

    /**
     * 删除所有文档。
     * 该方法会删除索引中的所有文档，并提交更改，同时刷新搜索管理器以反映最新的索引状态。
     *
     * @throws IOException 如果在删除文档或提交更改的过程中发生I/O错误。
     */
    public static void deleteAll() throws IOException {
        writer.deleteAll();
        writer.commit();
        searcherManager.maybeRefresh();
    }


    public static List<LuceHit> searchByVector(float[] vec, String cameraId, String groupId, Integer topN) {
        List<LuceHit> results = new ArrayList<>();
        try {
            // 1. 向量查询
            Query knnQuery = new KnnFloatVectorQuery(VECTOR_FIELD, vec, topN);
            // 2. 布尔过滤条件（cameraId / groupId）
            BooleanQuery.Builder builder = new BooleanQuery.Builder();
            builder.add(knnQuery, BooleanClause.Occur.MUST);
            if (!OPEN_DETECT) {
                // 如果不启用目标检测，就不用关注子图
                builder.add(new TermQuery(new Term("main", String.valueOf(1))), BooleanClause.Occur.FILTER);
            }
            if (cameraId != null) {
                builder.add(new TermQuery(new Term("camera_id", cameraId)), BooleanClause.Occur.FILTER);
            }
            if (groupId != null) {
                builder.add(new TermQuery(new Term("group_id", groupId)), BooleanClause.Occur.FILTER);
            }
            Query finalQuery = builder.build();
            // 3. 执行查询
            TopDocs topDocs = searcher.search(finalQuery, topN);
            // 4. 遍历结果，构建 LuceHit
            for (ScoreDoc sd : topDocs.scoreDocs) {
                Document doc = searcher.doc(sd.doc);
                // 坐标框
                int isMain = Integer.parseInt(doc.get("main"));
                Box box = null;
                if (isMain == 0) { // 非主图 → 有框
                    box = new Box(
                            Float.parseFloat(doc.get("box_x1")),
                            Float.parseFloat(doc.get("box_y1")),
                            Float.parseFloat(doc.get("box_x2")),
                            Float.parseFloat(doc.get("box_y2")),
                            0, "", 0
                    );
                }
                // 元信息
                Map<String, String> meta = JsonUtils.json2Map(doc.get("meta_json")); // 使用你之前的 Jackson 工具类
                LuceHit hit = new LuceHit(
                        doc.get("image_id"),
                        doc.get("img_url"),
                        box,
                        sd.score, // Lucene 的打分（不是余弦相似度，但也可排序）
                        doc.get("camera_id"),
                        doc.get("group_id"),
                        meta
                );

                results.add(hit);
            }

        } catch (IOException e) {
            throw new RuntimeException("Lucene search failed", e);
        }

        return results;
    }

    public static List<LuceHit> searchById(String imgId) {
        List<LuceHit> results = new ArrayList<>();
        try {
            BooleanQuery.Builder builder = new BooleanQuery.Builder();
            builder.add(new TermQuery(new Term("image_id", imgId)), BooleanClause.Occur.FILTER);
            Query finalQuery = builder.build();
            TopDocs topDocs = searcher.search(finalQuery, 999999);

            for (ScoreDoc sd : topDocs.scoreDocs) {
                Document doc = searcher.doc(sd.doc);
                // 坐标框
                int isMain = Integer.parseInt(doc.get("main"));
                Box box = null;
                if (isMain == 0) { // 非主图 → 有框
                    box = new Box(
                            Float.parseFloat(doc.get("box_x1")),
                            Float.parseFloat(doc.get("box_y1")),
                            Float.parseFloat(doc.get("box_x2")),
                            Float.parseFloat(doc.get("box_y2")),
                            0, "", 0
                    );
                }
                // 元信息
                Map<String, String> meta = JsonUtils.json2Map(doc.get("meta_json")); // 使用你之前的 Jackson 工具类
                LuceHit hit = new LuceHit(
                        doc.get("image_id"),
                        doc.get("img_url"),
                        box,
                        sd.score, // Lucene 的打分（不是余弦相似度，但也可排序）
                        doc.get("camera_id"),
                        doc.get("group_id"),
                        meta
                );

                results.add(hit);
            }
        } catch (IOException e) {
            throw new RuntimeException("Lucene search failed", e);
        }
        return results;
    }
}
