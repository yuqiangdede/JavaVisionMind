package com.yuqiangdede.ffe.util;

import com.yuqiangdede.common.util.VectorUtil;
import com.yuqiangdede.ffe.dto.output.FaceInfo4Search;
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

public class FfeLuceneUtil {
    private static SearcherManager searcherManager;
    private static IndexWriter writer;
    private static FSDirectory directory;



    /**
     * 初始化索引功能
     *
     * @throws Exception 如果初始化过程中出现错误，则抛出异常
     */
    public static void init(String indexPath) throws Exception {
        directory = FSDirectory.open(Paths.get(indexPath));

        // 创建 IndexWriter
        IndexWriterConfig config = new IndexWriterConfig();
        writer = new IndexWriter(directory, config);

        // 初始化 SearcherManager
        searcherManager = new SearcherManager(writer, new SearcherFactory());

        // 提交并刷新 SearcherManager
        writer.commit();
        searcherManager.maybeRefresh();
    }

    public static void close() throws Exception {
        // 关闭资源
        searcherManager.close();
        writer.close();
        directory.close();
    }

    /**
     * 添加文档的方法
     *
     * @param vector  人脸的向量信息
     * @param imgUrl  人脸图像URL
     * @param id      人脸唯一标识符
     * @param groupId 人脸分组ID
     * @throws IOException 如果添加文档过程中出现错误，则抛出异常
     */
    // 添加文档的方法
    public static void add(float[] vector, String imgUrl, String id, String groupId) throws IOException {
        Document doc = new Document();
        // 向量索引 暂时不需要存储
        doc.add(new KnnFloatVectorField("vector", VectorUtil.normalizeVector(vector)));

        // 时间字段
        long time = System.currentTimeMillis();
        doc.add(new LongPoint("time", time));
        doc.add(new StoredField("time_stored", time));

        doc.add(new StringField("groupId", groupId, Field.Store.YES));
        doc.add(new StringField("id", id, Field.Store.YES));
        doc.add(new StringField("imgUrl", imgUrl, Field.Store.YES));
        // 添加文档到索引
        writer.addDocument(doc);
        writer.commit();
        searcherManager.maybeRefresh();
    }

    /**
     * 根据给定的ID删除对应的文档。
     *
     * @param id 需要删除的文档的ID。
     * @throws IOException 如果在删除文档的过程中发生I/O错误。
     */
    public static void delete(String id) throws IOException {
        writer.deleteDocuments(new Term("id", id));
        writer.commit();
        searcherManager.maybeRefresh();
    }

    /**
     * 删除所有文档。
     * 该方法会删除索引中的所有文档，并提交更改，同时刷新搜索管理器以反映最新的索引状态。
     * @throws IOException 如果在删除文档或提交更改的过程中发生I/O错误。
     */
    public static void deleteAll() throws IOException {
        writer.deleteAll();
        writer.commit();
        searcherManager.maybeRefresh();
    }

    // 执行搜索的方法
    public static List<FaceInfo4Search> searchTop(float[] queryVector, String groupId, float confThreshold, int n) throws IOException {
        List<FaceInfo4Search> face4Search = new ArrayList<>();

        // 获取 IndexSearcher
        IndexSearcher searcher = searcherManager.acquire();
        try {
            // 组合查询条件
            BooleanQuery.Builder finalQueryBuilder = new BooleanQuery.Builder();

            // 构建 KnnVectorQuery
            KnnFloatVectorQuery knnQuery = new KnnFloatVectorQuery("vector", VectorUtil.normalizeVector(queryVector), 100);
            finalQueryBuilder.add(knnQuery, BooleanClause.Occur.MUST);

            if (groupId != null) {
                Query resourceQuery = new TermQuery(new Term("groupId", groupId));
                finalQueryBuilder.add(resourceQuery, BooleanClause.Occur.FILTER);
            }

            // 执行查询
            Query finalQuery = finalQueryBuilder.build();
            TopDocs topDocs = searcher.search(finalQuery, n);

            // 解析结果
            for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
                if (scoreDoc.score < confThreshold) {
                    // 如果得分低于阈值，则跳过该结果
                    continue;
                }
                Document doc = searcher.doc(scoreDoc.doc);
                String id = doc.get("id");
                String imgUrl = doc.get("imgUrl");
                float confidence = scoreDoc.score;

                face4Search.add(new FaceInfo4Search(id, imgUrl, confidence));
            }

        } finally {
            searcherManager.release(searcher);
        }
        return face4Search;
    }

}
