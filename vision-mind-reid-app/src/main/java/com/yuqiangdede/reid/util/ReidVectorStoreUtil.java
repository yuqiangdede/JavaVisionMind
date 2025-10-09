package com.yuqiangdede.reid.util;

import com.yuqiangdede.common.chroma.ChromaStore;
import com.yuqiangdede.common.chroma.EmbeddingRecord;
import com.yuqiangdede.common.chroma.InMemoryChromaStore;
import com.yuqiangdede.common.chroma.SearchResult;
import com.yuqiangdede.common.util.RandomProjectionUtils;
import com.yuqiangdede.common.util.VectorUtil;
import com.yuqiangdede.reid.output.Feature;
import com.yuqiangdede.reid.output.Human;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.KnnFloatVectorField;
import org.apache.lucene.document.LongPoint;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.KnnFloatVectorQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.SearcherFactory;
import org.apache.lucene.search.SearcherManager;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.store.FSDirectory;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Vector storage helper for ReID that can switch between Lucene persistence and
 * an in-memory chroma-compatible store.
 */
public final class ReidVectorStoreUtil {
    private static final String VECTOR_FIELD = "vector";
    private static final Object LUCENE_LOCK = new Object();

    private static volatile boolean persistenceEnabled;
    private static SearcherManager searcherManager;
    private static IndexWriter writer;
    private static FSDirectory directory;
    private static ChromaStore inMemoryStore;

    private ReidVectorStoreUtil() {
    }

    /**
     * Initialise storage layer.
     *
     * @param indexPath location for Lucene indices
     * @param persistVectors whether vectors should be persisted to disk
     */
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

    public static void add(String imgUrl, String cameraId, String humanId, Feature feature) {
        Objects.requireNonNull(feature, "feature");
        if (persistenceEnabled) {
            addToLucene(imgUrl, cameraId, humanId, feature);
        } else {
            addToMemory(imgUrl, cameraId, humanId, feature);
        }
    }

    public static void delete(String id) throws IOException {
        if (persistenceEnabled) {
            synchronized (LUCENE_LOCK) {
                writer.deleteDocuments(new Term("image_id", id));
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

    public static List<Human> searchByVector(float[] vec, String cameraId, Integer topN, float confThreshold) {
        if (persistenceEnabled) {
            return searchWithLucene(projectForLucene(vec), cameraId, topN, confThreshold);
        }
        return searchInMemory(vec, cameraId, topN, confThreshold);
    }

    private static float[] projectForLucene(float[] source) {
        if (source == null) {
            throw new IllegalArgumentException("Vector source must not be null");
        }
        if (source.length > 1024) {
            return VectorUtil.normalizeVector(RandomProjectionUtils.transform(source));
        }
        if (source.length == 1024) {
            return VectorUtil.normalizeVector(source);
        }
        throw new IllegalArgumentException("Unsupported vector length: " + source.length);
    }

    private static void addToLucene(String imgUrl, String cameraId, String humanId, Feature feature) {
        Document doc = new Document();
        doc.add(new KnnFloatVectorField(VECTOR_FIELD, projectForLucene(feature.getEmbeds())));
        doc.add(new StringField("image_id", feature.getUuid(), Field.Store.YES));
        if (imgUrl != null) {
            doc.add(new StoredField("img_url", imgUrl));
        }
        if (cameraId != null) {
            doc.add(new StringField("camera_id", cameraId, Field.Store.YES));
        }
        String resolvedHumanId = humanId != null ? humanId : feature.getUuid();
        doc.add(new StringField("human_id", resolvedHumanId, Field.Store.YES));
        long now = System.currentTimeMillis();
        doc.add(new LongPoint("timestamp", now));
        doc.add(new StoredField("timestamp", now));

        try {
            synchronized (LUCENE_LOCK) {
                writer.addDocument(doc);
                writer.commit();
                searcherManager.maybeRefresh();
            }
        } catch (IOException e) {
            throw new RuntimeException("Lucene write failed", e);
        }
    }

    private static void addToMemory(String imgUrl, String cameraId, String humanId, Feature feature) {
        if (inMemoryStore == null) {
            throw new IllegalStateException("In-memory vector store is not initialised");
        }
        Map<String, String> metadata = new HashMap<>();
        if (cameraId != null) {
            metadata.put("camera_id", cameraId);
        }
        String resolvedHumanId = humanId != null ? humanId : feature.getUuid();
        metadata.put("human_id", resolvedHumanId);
        metadata.put("image_id", feature.getUuid());

        Map<String, Object> payload = new HashMap<>();
        if (imgUrl != null) {
            payload.put("img_url", imgUrl);
        }

        EmbeddingRecord record = new EmbeddingRecord(
                feature.getUuid(),
                VectorUtil.normalizeVector(feature.getEmbeds()),
                metadata,
                payload,
                System.currentTimeMillis());
        inMemoryStore.upsert(record);
    }

    private static List<Human> searchWithLucene(float[] vec, String cameraId, Integer topN, float confThreshold) {
        List<Human> results = new ArrayList<>();
        try {
            SearcherManager manager;
            synchronized (LUCENE_LOCK) {
                manager = searcherManager;
            }
            if (manager == null) {
                return results;
            }
            int limit = topN == null ? 10 : topN;
            if (limit <= 0) {
                return results;
            }
            IndexSearcher searcher = manager.acquire();
            try {
                Query knnQuery = new KnnFloatVectorQuery(VECTOR_FIELD, vec, limit);
                BooleanQuery.Builder builder = new BooleanQuery.Builder();
                builder.add(knnQuery, BooleanClause.Occur.MUST);
                if (cameraId != null) {
                    builder.add(new TermQuery(new Term("camera_id", cameraId)), BooleanClause.Occur.FILTER);
                }
                TopDocs topDocs = searcher.search(builder.build(), limit);
                for (ScoreDoc sd : topDocs.scoreDocs) {
                    if (sd.score < confThreshold) {
                        continue;
                    }
                    Document doc = searcher.storedFields().document(sd.doc);
                    Human hit = new Human(
                            doc.get("human_id"),
                            doc.get("image_id"),
                            doc.get("img_url"),
                            sd.score,
                            doc.get("camera_id"),
                            "exist"
                    );
                    results.add(hit);
                }
            } finally {
                manager.release(searcher);
            }
        } catch (IOException e) {
            throw new RuntimeException("Lucene search failed", e);
        }
        return results;
    }

    private static List<Human> searchInMemory(float[] vec, String cameraId, Integer topN, float confThreshold) {
        if (inMemoryStore == null) {
            return List.of();
        }
        Map<String, String> filter = new HashMap<>();
        if (cameraId != null) {
            filter.put("camera_id", cameraId);
        }
        List<SearchResult> hits = inMemoryStore.similaritySearch(
                VectorUtil.normalizeVector(vec),
                topN == null ? 10 : topN,
                filter,
                confThreshold);
        List<Human> humans = new ArrayList<>(hits.size());
        for (SearchResult hit : hits) {
            EmbeddingRecord record = hit.getRecord();
            String humanId = record.getMetadata().getOrDefault("human_id", record.getId());
            String imageId = record.getMetadata().getOrDefault("image_id", record.getId());
            Object imgUrlObj = record.getPayload().get("img_url");
            String imgUrl = imgUrlObj instanceof String ? (String) imgUrlObj : null;
            String cameraValue = record.getMetadata().get("camera_id");
            humans.add(new Human(
                    humanId,
                    imageId,
                    imgUrl,
                    (float) hit.getScore(),
                    cameraValue,
                    "exist"));
        }
        return humans;
    }
}


