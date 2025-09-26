package com.yuqiangdede.ffe.util;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.KnnFloatVectorField;
import org.apache.lucene.document.LongPoint;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.StoredFields;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.KnnFloatVectorQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.SearcherFactory;
import org.apache.lucene.search.SearcherManager;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.FSDirectory;

import com.yuqiangdede.common.chroma.ChromaStore;
import com.yuqiangdede.common.chroma.EmbeddingRecord;
import com.yuqiangdede.common.chroma.InMemoryChromaStore;
import com.yuqiangdede.common.chroma.SearchResult;
import com.yuqiangdede.common.util.VectorUtil;
import com.yuqiangdede.ffe.dto.output.FaceInfo4Search;

/**
 * Shared utility that supports Lucene persistence or an in-memory chroma store.
 */
public final class FfeLuceneUtil {
    private static final Object LUCENE_LOCK = new Object();

    private static volatile boolean persistenceEnabled;
    private static SearcherManager searcherManager;
    private static IndexWriter writer;
    private static FSDirectory directory;
    private static ChromaStore inMemoryStore;

    private FfeLuceneUtil() {
    }

    public static void init(String indexPath, boolean persistVectors) throws Exception {
        persistenceEnabled = persistVectors;
        if (persistVectors) {
            synchronized (LUCENE_LOCK) {
                directory = FSDirectory.open(Paths.get(indexPath));
                IndexWriterConfig config = new IndexWriterConfig();
                config.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);
                writer = new IndexWriter(directory, config);
                searcherManager = new SearcherManager(writer, new SearcherFactory());
                writer.commit();
                searcherManager.maybeRefresh();
            }
        } else {
            inMemoryStore = new InMemoryChromaStore();
        }
    }

    public static void close() throws Exception {
        if (persistenceEnabled) {
            synchronized (LUCENE_LOCK) {
                if (searcherManager != null) {
                    searcherManager.close();
                    searcherManager = null;
                }
                if (writer != null) {
                    writer.close();
                    writer = null;
                }
                if (directory != null) {
                    directory.close();
                    directory = null;
                }
            }
        } else {
            inMemoryStore = null;
        }
    }

    public static void add(float[] vector, String imgUrl, String id, String groupId) throws IOException {
        Objects.requireNonNull(vector, "vector");
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(groupId, "groupId");
        if (persistenceEnabled) {
            addToLucene(vector, imgUrl, id, groupId);
        } else {
            addToMemory(vector, imgUrl, id, groupId);
        }
    }

    public static void delete(String id) throws IOException {
        if (persistenceEnabled) {
            synchronized (LUCENE_LOCK) {
                writer.deleteDocuments(new Term("id", id));
                writer.commit();
                searcherManager.maybeRefresh();
            }
        } else if (inMemoryStore != null) {
            inMemoryStore.delete(id);
        }
    }

    public static void deleteAll() throws IOException {
        if (persistenceEnabled) {
            synchronized (LUCENE_LOCK) {
                writer.deleteAll();
                writer.commit();
                searcherManager.maybeRefresh();
            }
        } else if (inMemoryStore != null) {
            inMemoryStore.clear();
        }
    }

    public static List<FaceInfo4Search> searchTop(float[] queryVector, String groupId, float confThreshold, int n) throws IOException {
        if (persistenceEnabled) {
            return searchWithLucene(queryVector, groupId, confThreshold, n);
        }
        return searchInMemory(queryVector, groupId, confThreshold, n);
    }

    private static void addToLucene(float[] vector, String imgUrl, String id, String groupId) throws IOException {
        Document doc = new Document();
        doc.add(new KnnFloatVectorField("vector", VectorUtil.normalizeVector(vector)));
        long time = System.currentTimeMillis();
        doc.add(new LongPoint("time", time));
        doc.add(new StoredField("time_stored", time));
        doc.add(new StringField("groupId", groupId, Field.Store.YES));
        doc.add(new StringField("id", id, Field.Store.YES));
        doc.add(new StringField("imgUrl", imgUrl, Field.Store.YES));
        synchronized (LUCENE_LOCK) {
            writer.addDocument(doc);
            writer.commit();
            searcherManager.maybeRefresh();
        }
    }

    private static void addToMemory(float[] vector, String imgUrl, String id, String groupId) {
        if (inMemoryStore == null) {
            throw new IllegalStateException("In-memory vector store is not initialised");
        }
        Map<String, String> metadata = new HashMap<>();
        metadata.put("groupId", groupId);
        metadata.put("id", id);
        Map<String, Object> payload = new HashMap<>();
        payload.put("imgUrl", imgUrl);
        payload.put("time", System.currentTimeMillis());
        EmbeddingRecord record = new EmbeddingRecord(
                id,
                VectorUtil.normalizeVector(vector),
                metadata,
                payload,
                System.currentTimeMillis());
        inMemoryStore.upsert(record);
    }

    private static List<FaceInfo4Search> searchWithLucene(float[] queryVector, String groupId, float confThreshold, int n) throws IOException {
        List<FaceInfo4Search> face4Search = new ArrayList<>();
        SearcherManager manager;
        synchronized (LUCENE_LOCK) {
            manager = searcherManager;
        }
        if (manager == null) {
            return face4Search;
        }
        int limit = Math.max(n, 0);
        if (limit == 0) {
            return face4Search;
        }
        IndexSearcher searcher = manager.acquire();
        try {
            BooleanQuery.Builder finalQueryBuilder = new BooleanQuery.Builder();
            KnnFloatVectorQuery knnQuery = new KnnFloatVectorQuery("vector", VectorUtil.normalizeVector(queryVector), Math.max(limit, 1));
            finalQueryBuilder.add(knnQuery, BooleanClause.Occur.MUST);
            if (groupId != null) {
                Query resourceQuery = new TermQuery(new Term("groupId", groupId));
                finalQueryBuilder.add(resourceQuery, BooleanClause.Occur.FILTER);
            }
            TopDocs topDocs = searcher.search(finalQueryBuilder.build(), limit);
            StoredFields storedFields = searcher.storedFields();
            Set<String> fields = new HashSet<>();
            fields.add("id");
            fields.add("imgUrl");
            for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
                if (scoreDoc.score < confThreshold) {
                    continue;
                }
                Document doc = storedFields.document(scoreDoc.doc, fields);
                String id = doc.get("id");
                String imgUrl = doc.get("imgUrl");
                float confidence = scoreDoc.score;
                face4Search.add(new FaceInfo4Search(id, imgUrl, confidence));
            }
        } finally {
            manager.release(searcher);
        }
        return face4Search;
    }

    private static List<FaceInfo4Search> searchInMemory(float[] queryVector, String groupId, float confThreshold, int n) {
        if (inMemoryStore == null) {
            return List.of();
        }
        int limit = Math.max(n, 0);
        if (limit == 0) {
            return List.of();
        }
        Map<String, String> filter = new HashMap<>();
        if (groupId != null) {
            filter.put("groupId", groupId);
        }
        List<SearchResult> results = inMemoryStore.similaritySearch(
                VectorUtil.normalizeVector(queryVector),
                limit,
                filter,
                confThreshold);
        List<FaceInfo4Search> face4Search = new ArrayList<>(results.size());
        for (SearchResult result : results) {
            EmbeddingRecord record = result.getRecord();
            String id = record.getMetadata().getOrDefault("id", record.getId());
            Object imgUrlObj = record.getPayload().get("imgUrl");
            String imgUrl = imgUrlObj instanceof String ? (String) imgUrlObj : null;
            face4Search.add(new FaceInfo4Search(id, imgUrl, (float) result.getScore()));
        }
        return face4Search;
    }
}
