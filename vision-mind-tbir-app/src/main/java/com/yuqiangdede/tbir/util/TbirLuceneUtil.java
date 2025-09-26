package com.yuqiangdede.tbir.util;

import com.yuqiangdede.common.chroma.ChromaStore;
import com.yuqiangdede.common.chroma.EmbeddingRecord;
import com.yuqiangdede.common.chroma.InMemoryChromaStore;
import com.yuqiangdede.common.chroma.SearchResult;
import com.yuqiangdede.common.dto.output.Box;
import com.yuqiangdede.common.util.JsonUtils;
import com.yuqiangdede.common.util.VectorUtil;
import com.yuqiangdede.tbir.dto.ImageEmbedding;
import com.yuqiangdede.tbir.dto.LuceHit;
import com.yuqiangdede.tbir.dto.input.SaveImageRequest;
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
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static com.yuqiangdede.tbir.config.Constant.OPEN_DETECT;

/**
 * Utility wrapping vector storage for TBIR. Supports Lucene persistence or an in-memory chroma alternative.
 */
public final class TbirLuceneUtil {
    private static final String VECTOR_FIELD = "vector";
    private static final Object LUCENE_LOCK = new Object();

    private static volatile boolean persistenceEnabled;
    private static SearcherManager searcherManager;
    private static IndexWriter writer;
    private static FSDirectory directory;
    private static ChromaStore inMemoryStore;
    private static final Map<String, Set<String>> memoryIndex = new ConcurrentHashMap<>();

    private TbirLuceneUtil() {
    }

    public static void init(String indexPath, boolean persistVectors) throws IOException {
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
            memoryIndex.clear();
        }
    }

    public static void close() throws IOException {
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
            memoryIndex.clear();
        }
    }

    public static void add(String imageId, ImageEmbedding emb, SaveImageRequest input) {
        Objects.requireNonNull(imageId, "imageId");
        Objects.requireNonNull(emb, "emb");
        Objects.requireNonNull(input, "input");
        if (persistenceEnabled) {
            addToLucene(imageId, emb, input);
        } else {
            addToMemory(imageId, emb, input);
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
            Set<String> docIds = memoryIndex.remove(id);
            if (docIds != null) {
                for (String docId : docIds) {
                    inMemoryStore.delete(docId);
                }
            }
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
            memoryIndex.clear();
        }
    }

    public static List<LuceHit> searchByVector(float[] vec, String cameraId, String groupId, Integer topN) {
        if (persistenceEnabled) {
            return searchWithLucene(vec, cameraId, groupId, topN);
        }
        return searchInMemory(vec, cameraId, groupId, topN);
    }

    public static List<LuceHit> searchById(String imgId) {
        if (persistenceEnabled) {
            return searchByIdWithLucene(imgId);
        }
        return searchByIdInMemory(imgId);
    }

    private static void addToLucene(String imageId, ImageEmbedding emb, SaveImageRequest input) {
        Document doc = new Document();
        doc.add(new KnnFloatVectorField(VECTOR_FIELD, emb.getVector()));
        doc.add(new StringField("image_id", imageId, Field.Store.YES));
        doc.add(new StringField("main", emb.isMainImage() ? "1" : "0", Field.Store.YES));
        if (input.getImgUrl() != null) {
            doc.add(new StoredField("img_url", input.getImgUrl()));
        }
        Box sourceBox = emb.getSourceBox();
        if (sourceBox != null) {
            doc.add(new StoredField("box_x1", sourceBox.getX1()));
            doc.add(new StoredField("box_y1", sourceBox.getY1()));
            doc.add(new StoredField("box_x2", sourceBox.getX2()));
            doc.add(new StoredField("box_y2", sourceBox.getY2()));
        }
        if (input.getCameraId() != null) {
            doc.add(new StringField("camera_id", input.getCameraId(), Field.Store.YES));
        }
        if (input.getGroupId() != null) {
            doc.add(new StringField("group_id", input.getGroupId(), Field.Store.YES));
        }
        if (input.getMeta() != null && !input.getMeta().isEmpty()) {
            String metaJson = JsonUtils.map2Json(input.getMeta());
            doc.add(new StoredField("meta_json", metaJson));
        }
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

    private static void addToMemory(String imageId, ImageEmbedding emb, SaveImageRequest input) {
        if (inMemoryStore == null) {
            throw new IllegalStateException("In-memory vector store is not initialised");
        }
        String docId = imageId + ":" + UUID.randomUUID();
        Map<String, String> metadata = new HashMap<>();
        metadata.put("image_id", imageId);
        metadata.put("main", emb.isMainImage() ? "1" : "0");
        if (input.getCameraId() != null) {
            metadata.put("camera_id", input.getCameraId());
        }
        if (input.getGroupId() != null) {
            metadata.put("group_id", input.getGroupId());
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("img_url", input.getImgUrl());
        payload.put("box", emb.getSourceBox());
        payload.put("meta", input.getMeta());
        EmbeddingRecord record = new EmbeddingRecord(
                docId,
                VectorUtil.normalizeVector(emb.getVector()),
                metadata,
                payload,
                System.currentTimeMillis());
        inMemoryStore.upsert(record);
        memoryIndex.computeIfAbsent(imageId, key -> Collections.newSetFromMap(new ConcurrentHashMap<>()))
                .add(docId);
    }

    private static List<LuceHit> searchWithLucene(float[] vec, String cameraId, String groupId, Integer topN) {
        List<LuceHit> results = new ArrayList<>();
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
                if (!OPEN_DETECT) {
                    builder.add(new TermQuery(new Term("main", "1")), BooleanClause.Occur.FILTER);
                }
                if (cameraId != null) {
                    builder.add(new TermQuery(new Term("camera_id", cameraId)), BooleanClause.Occur.FILTER);
                }
                if (groupId != null) {
                    builder.add(new TermQuery(new Term("group_id", groupId)), BooleanClause.Occur.FILTER);
                }
                TopDocs topDocs = searcher.search(builder.build(), limit);
                StoredFields storedFields = searcher.storedFields();
                Set<String> requiredFields = new HashSet<>();
                Collections.addAll(requiredFields,
                        "main", "box_x1", "box_y1", "box_x2", "box_y2",
                        "image_id", "img_url", "camera_id", "group_id", "meta_json");
                for (ScoreDoc sd : topDocs.scoreDocs) {
                    Document doc = storedFields.document(sd.doc, requiredFields);
                    results.add(buildHitFromDocument(doc, sd.score));
                }
            } finally {
                manager.release(searcher);
            }
        } catch (IOException e) {
            throw new RuntimeException("Lucene search failed", e);
        }
        return results;
    }

    private static List<LuceHit> searchInMemory(float[] vec, String cameraId, String groupId, Integer topN) {
        if (inMemoryStore == null) {
            return List.of();
        }
        int limit = topN == null ? 10 : topN;
        if (limit <= 0) {
            return List.of();
        }
        Map<String, String> filter = new HashMap<>();
        if (!OPEN_DETECT) {
            filter.put("main", "1");
        }
        if (cameraId != null) {
            filter.put("camera_id", cameraId);
        }
        if (groupId != null) {
            filter.put("group_id", groupId);
        }
        List<SearchResult> hits = inMemoryStore.similaritySearch(
                VectorUtil.normalizeVector(vec),
                limit,
                filter,
                -Double.MAX_VALUE);
        List<LuceHit> results = new ArrayList<>(hits.size());
        for (SearchResult hit : hits) {
            results.add(buildHitFromRecord(hit.getRecord(), (float) hit.getScore()));
        }
        return results;
    }

    private static List<LuceHit> searchByIdWithLucene(String imgId) {
        List<LuceHit> results = new ArrayList<>();
        try {
            SearcherManager manager;
            synchronized (LUCENE_LOCK) {
                manager = searcherManager;
            }
            if (manager == null) {
                return results;
            }
            IndexSearcher searcher = manager.acquire();
            try {
                BooleanQuery.Builder builder = new BooleanQuery.Builder();
                builder.add(new TermQuery(new Term("image_id", imgId)), BooleanClause.Occur.FILTER);
                TopDocs topDocs = searcher.search(builder.build(), Integer.MAX_VALUE);
                StoredFields storedFields = searcher.storedFields();
                Set<String> requiredFields = new HashSet<>();
                Collections.addAll(requiredFields,
                        "main", "box_x1", "box_y1", "box_x2", "box_y2",
                        "image_id", "img_url", "camera_id", "group_id", "meta_json");
                for (ScoreDoc sd : topDocs.scoreDocs) {
                    Document doc = storedFields.document(sd.doc, requiredFields);
                    results.add(buildHitFromDocument(doc, sd.score));
                }
            } finally {
                manager.release(searcher);
            }
        } catch (IOException e) {
            throw new RuntimeException("Lucene search failed", e);
        }
        return results;
    }

    private static List<LuceHit> searchByIdInMemory(String imgId) {
        if (inMemoryStore == null) {
            return List.of();
        }
        Map<String, String> filter = Map.of("image_id", imgId);
        List<EmbeddingRecord> records = inMemoryStore.find(filter);
        List<LuceHit> hits = new ArrayList<>(records.size());
        for (EmbeddingRecord record : records) {
            hits.add(buildHitFromRecord(record, 1f));
        }
        return hits;
    }

    private static LuceHit buildHitFromDocument(Document doc, float score) {
        int isMain = Integer.parseInt(doc.get("main"));
        Box box = null;
        if (isMain == 0 && doc.get("box_x1") != null) {
            box = new Box(
                    Float.parseFloat(doc.get("box_x1")),
                    Float.parseFloat(doc.get("box_y1")),
                    Float.parseFloat(doc.get("box_x2")),
                    Float.parseFloat(doc.get("box_y2")),
                    0, "", 0
            );
        }
        Map<String, String> meta = JsonUtils.json2Map(doc.get("meta_json"));
        return new LuceHit(
                doc.get("image_id"),
                doc.get("img_url"),
                box,
                score,
                doc.get("camera_id"),
                doc.get("group_id"),
                meta
        );
    }

    @SuppressWarnings("unchecked")
    private static LuceHit buildHitFromRecord(EmbeddingRecord record, float score) {
        Map<String, Object> payload = record.getPayload();
        Box box = null;
        Object boxObj = payload.get("box");
        if (boxObj instanceof Box) {
            box = (Box) boxObj;
        }
        Map<String, String> meta = Collections.emptyMap();
        Object metaObj = payload.get("meta");
        if (metaObj instanceof Map<?, ?> map) {
            meta = (Map<String, String>) map;
        }
        return new LuceHit(
                record.getMetadata().get("image_id"),
                (String) payload.get("img_url"),
                box,
                score,
                record.getMetadata().get("camera_id"),
                record.getMetadata().get("group_id"),
                meta
        );
    }
}
