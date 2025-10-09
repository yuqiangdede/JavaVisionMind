package com.yuqiangdede.tbir.util;

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
import com.yuqiangdede.common.dto.output.Box;
import com.yuqiangdede.common.util.JsonUtils;
import com.yuqiangdede.common.util.VectorUtil;
import com.yuqiangdede.common.vector.ElasticsearchClientFactory;
import com.yuqiangdede.common.vector.ElasticsearchConfig;
import com.yuqiangdede.common.vector.VectorStoreMode;
import static com.yuqiangdede.tbir.config.Constant.OPEN_DETECT;
import com.yuqiangdede.tbir.dto.ImageEmbedding;
import com.yuqiangdede.tbir.dto.LuceHit;
import com.yuqiangdede.tbir.dto.input.SaveImageRequest;

import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.support.WriteRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.CreateIndexRequest;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.functionscore.FunctionScoreQueryBuilder;
import org.elasticsearch.index.query.functionscore.ScriptScoreFunctionBuilder;
import org.elasticsearch.index.reindex.DeleteByQueryRequest;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;

/**
 * Utility wrapping vector storage for TBIR. Supports Lucene persistence or an in-memory chroma alternative.
 */
public final class TbirVectorStoreUtil {
    private static final String VECTOR_FIELD = "vector";
    private static final Object LUCENE_LOCK = new Object();
    private static final Object ES_LOCK = new Object();

    private static volatile VectorStoreMode mode = VectorStoreMode.MEMORY;
    private static SearcherManager searcherManager;
    private static IndexWriter writer;
    private static FSDirectory directory;
    private static ChromaStore inMemoryStore;
    private static final Map<String, Set<String>> memoryIndex = new ConcurrentHashMap<>();

    private static RestHighLevelClient esClient;
    private static ElasticsearchConfig esConfig;
    private static boolean esIndexReady;

    private TbirVectorStoreUtil() {
    }

    public static void init(String indexPath, VectorStoreMode storeMode, ElasticsearchConfig config) throws IOException {
        close();
        mode = storeMode == null ? VectorStoreMode.LUCENE : storeMode;
        switch (mode) {
            case LUCENE -> initLucene(indexPath);
            case MEMORY -> {
                inMemoryStore = new InMemoryChromaStore();
                memoryIndex.clear();
            }
            case ELASTICSEARCH -> initElasticsearch(config);
            default -> throw new IllegalStateException("Unsupported vector store mode: " + mode);
        }
    }

    private static void initLucene(String indexPath) throws IOException {
        synchronized (LUCENE_LOCK) {
            directory = FSDirectory.open(Paths.get(indexPath));
            IndexWriterConfig config = new IndexWriterConfig();
            config.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);
            writer = new IndexWriter(directory, config);
            searcherManager = new SearcherManager(writer, new SearcherFactory());
            writer.commit();
            searcherManager.maybeRefresh();
        }
    }

    private static void initElasticsearch(ElasticsearchConfig config) {
        if (config == null || !config.hasValidIndex()) {
            throw new IllegalArgumentException("Elasticsearch configuration must include an index name.");
        }
        esConfig = config;
        esClient = ElasticsearchClientFactory.createClient(
                config.getUris(),
                config.getUsername(),
                config.getPassword(),
                config.getApiKey());
        esIndexReady = false;
    }

    public static void close() throws IOException {
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
        inMemoryStore = null;
        memoryIndex.clear();
        if (esClient != null) {
            esClient.close();
            esClient = null;
        }
        esConfig = null;
        esIndexReady = false;
    }

    public static void add(String imageId, ImageEmbedding emb, SaveImageRequest input) {
        Objects.requireNonNull(imageId, "imageId");
        Objects.requireNonNull(emb, "emb");
        Objects.requireNonNull(input, "input");
        switch (mode) {
            case LUCENE -> addToLucene(imageId, emb, input);
            case MEMORY -> addToMemory(imageId, emb, input);
            case ELASTICSEARCH -> addToElasticsearch(imageId, emb, input);
            default -> throw new IllegalStateException("Unsupported vector store mode: " + mode);
        }
    }

    public static void delete(String id) throws IOException {
        switch (mode) {
            case LUCENE -> {
                synchronized (LUCENE_LOCK) {
                    if (writer != null) {
                        writer.deleteDocuments(new Term("image_id", id));
                        writer.commit();
                        if (searcherManager != null) {
                            searcherManager.maybeRefresh();
                        }
                    }
                }
            }
            case MEMORY -> {
                if (inMemoryStore != null) {
                    Set<String> docIds = memoryIndex.remove(id);
                    if (docIds != null) {
                        for (String docId : docIds) {
                            inMemoryStore.delete(docId);
                        }
                    }
                }
            }
            case ELASTICSEARCH -> deleteFromElasticsearch(id);
            default -> throw new IllegalStateException("Unsupported vector store mode: " + mode);
        }
    }

    public static void deleteAll() throws IOException {
        switch (mode) {
            case LUCENE -> {
                synchronized (LUCENE_LOCK) {
                    if (writer != null) {
                        writer.deleteAll();
                        writer.commit();
                        if (searcherManager != null) {
                            searcherManager.maybeRefresh();
                        }
                    }
                }
            }
            case MEMORY -> {
                if (inMemoryStore != null) {
                    inMemoryStore.clear();
                }
                memoryIndex.clear();
            }
            case ELASTICSEARCH -> deleteAllFromElasticsearch();
            default -> throw new IllegalStateException("Unsupported vector store mode: " + mode);
        }
    }

    public static List<LuceHit> searchByVector(float[] vec, String cameraId, String groupId, Integer topN) {
        return switch (mode) {
            case LUCENE -> searchWithLucene(vec, cameraId, groupId, topN);
            case MEMORY -> searchInMemory(vec, cameraId, groupId, topN);
            case ELASTICSEARCH -> searchWithElasticsearch(vec, cameraId, groupId, topN);
            default -> throw new IllegalStateException("Unsupported vector store mode: " + mode);
        };
    }

    public static List<LuceHit> searchById(String imgId) {
        return switch (mode) {
            case LUCENE -> searchByIdWithLucene(imgId);
            case MEMORY -> searchByIdInMemory(imgId);
            case ELASTICSEARCH -> searchByIdWithElasticsearch(imgId);
            default -> throw new IllegalStateException("Unsupported vector store mode: " + mode);
        };
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

    private static void addToElasticsearch(String imageId, ImageEmbedding emb, SaveImageRequest input) {
        if (esClient == null || esConfig == null) {
            throw new IllegalStateException("Elasticsearch is not initialised");
        }
        try {
            ensureEsReady(emb.getVector().length);
            String docId = imageId + ":" + UUID.randomUUID();
            Map<String, Object> document = new HashMap<>();
            document.put("image_id", imageId);
            document.put("main", emb.isMainImage() ? "1" : "0");
            if (input.getImgUrl() != null) {
                document.put("img_url", input.getImgUrl());
            }
            if (input.getCameraId() != null) {
                document.put("camera_id", input.getCameraId());
            }
            if (input.getGroupId() != null) {
                document.put("group_id", input.getGroupId());
            }
            Box sourceBox = emb.getSourceBox();
            if (sourceBox != null) {
                document.put("box_x1", sourceBox.getX1());
                document.put("box_y1", sourceBox.getY1());
                document.put("box_x2", sourceBox.getX2());
                document.put("box_y2", sourceBox.getY2());
            }
            if (input.getMeta() != null && !input.getMeta().isEmpty()) {
                document.put("meta_json", JsonUtils.map2Json(input.getMeta()));
            }
            document.put("timestamp", System.currentTimeMillis());
            document.put(VECTOR_FIELD, floatArrayToList(VectorUtil.normalizeVector(emb.getVector())));

            IndexRequest request = new IndexRequest(esConfig.getIndex())
                    .id(docId)
                    .source(document)
                    .setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);
            esClient.index(request, RequestOptions.DEFAULT);
        } catch (IOException e) {
            throw new RuntimeException("Failed to persist vector to Elasticsearch", e);
        }
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

    private static List<LuceHit> searchWithElasticsearch(float[] vec, String cameraId, String groupId, Integer topN) {
        if (esClient == null || esConfig == null) {
            return List.of();
        }
        try {
            ensureEsReady(vec.length);
            int limit = topN == null ? 10 : topN;
            if (limit <= 0) {
                return List.of();
            }
            SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
            sourceBuilder.size(limit);
            Map<String, Object> params = new HashMap<>();
            params.put("query_vector", floatArrayToList(VectorUtil.normalizeVector(vec)));
            Script script = new Script(ScriptType.INLINE, "painless",
                    "double cosine = cosineSimilarity(params.query_vector, '" + VECTOR_FIELD + "'); return (cosine + 1.0) / 2.0;",
                    params);

            BoolQueryBuilder filter = QueryBuilders.boolQuery();
            if (!OPEN_DETECT) {
                filter.filter(QueryBuilders.termQuery("main", "1"));
            }
            if (cameraId != null) {
                filter.filter(QueryBuilders.termQuery("camera_id", cameraId));
            }
            if (groupId != null) {
                filter.filter(QueryBuilders.termQuery("group_id", groupId));
            }
            QueryBuilder baseQuery = filter.hasClauses() ? filter : QueryBuilders.matchAllQuery();
            ScriptScoreFunctionBuilder scriptFunction = new ScriptScoreFunctionBuilder(script);
            FunctionScoreQueryBuilder query = QueryBuilders.functionScoreQuery(baseQuery, scriptFunction);
            sourceBuilder.query(query);

            SearchRequest request = new SearchRequest(esConfig.getIndex());
            request.source(sourceBuilder);
            SearchResponse response = esClient.search(request, RequestOptions.DEFAULT);
            List<LuceHit> results = new ArrayList<>();
            for (SearchHit hit : response.getHits().getHits()) {
                results.add(buildHitFromSource(hit.getSourceAsMap(), hit.getScore()));
            }
            return results;
        } catch (IOException e) {
            throw new RuntimeException("Elasticsearch search failed", e);
        }
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

    private static List<LuceHit> searchByIdWithElasticsearch(String imgId) {
        if (esClient == null || esConfig == null) {
            return List.of();
        }
        try {
            ensureEsReady(1);
            SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
            sourceBuilder.query(QueryBuilders.termQuery("image_id", imgId));
            sourceBuilder.size(1000);
            SearchRequest request = new SearchRequest(esConfig.getIndex());
            request.source(sourceBuilder);
            SearchResponse response = esClient.search(request, RequestOptions.DEFAULT);
            List<LuceHit> results = new ArrayList<>();
            for (SearchHit hit : response.getHits().getHits()) {
                results.add(buildHitFromSource(hit.getSourceAsMap(), hit.getScore()));
            }
            return results;
        } catch (IOException e) {
            throw new RuntimeException("Elasticsearch search by id failed", e);
        }
    }

    private static void deleteFromElasticsearch(String imageId) throws IOException {
        if (esClient == null || esConfig == null) {
            return;
        }
        DeleteByQueryRequest request = new DeleteByQueryRequest(esConfig.getIndex());
        request.setQuery(QueryBuilders.termQuery("image_id", imageId));
        request.setRefresh(true);
        esClient.deleteByQuery(request, RequestOptions.DEFAULT);
    }

    private static void deleteAllFromElasticsearch() throws IOException {
        if (esClient == null || esConfig == null) {
            return;
        }
        DeleteByQueryRequest request = new DeleteByQueryRequest(esConfig.getIndex());
        request.setQuery(QueryBuilders.matchAllQuery());
        request.setRefresh(true);
        esClient.deleteByQuery(request, RequestOptions.DEFAULT);
        esIndexReady = false;
    }

    private static void ensureEsReady(int dims) throws IOException {
        if (esClient == null || esConfig == null) {
            throw new IllegalStateException("Elasticsearch is not initialised");
        }
        if (esIndexReady) {
            return;
        }
        synchronized (ES_LOCK) {
            if (esIndexReady) {
                return;
            }
            GetIndexRequest getIndex = new GetIndexRequest(esConfig.getIndex());
            boolean exists = esClient.indices().exists(getIndex, RequestOptions.DEFAULT);
            if (!exists) {
                CreateIndexRequest create = new CreateIndexRequest(esConfig.getIndex());
                Map<String, Object> vectorMapping = new HashMap<>();
                vectorMapping.put("type", "dense_vector");
                vectorMapping.put("dims", dims);
                vectorMapping.put("index", true);
                vectorMapping.put("similarity", "cosine");

                Map<String, Object> properties = new HashMap<>();
                properties.put(VECTOR_FIELD, vectorMapping);
                properties.put("image_id", Map.of("type", "keyword"));
                properties.put("main", Map.of("type", "keyword"));
                properties.put("img_url", Map.of("type", "keyword"));
                properties.put("camera_id", Map.of("type", "keyword"));
                properties.put("group_id", Map.of("type", "keyword"));
                properties.put("meta_json", Map.of("type", "keyword"));
                properties.put("box_x1", Map.of("type", "float"));
                properties.put("box_y1", Map.of("type", "float"));
                properties.put("box_x2", Map.of("type", "float"));
                properties.put("box_y2", Map.of("type", "float"));
                properties.put("timestamp", Map.of("type", "date"));

                Map<String, Object> mappings = Map.of("properties", properties);
                create.mapping(mappings);
                esClient.indices().create(create, RequestOptions.DEFAULT);
            }
            esIndexReady = true;
        }
    }

    private static LuceHit buildHitFromSource(Map<String, Object> source, float score) {
        String mainValue = source.get("main") != null ? String.valueOf(source.get("main")) : "0";
        int isMain;
        try {
            isMain = Integer.parseInt(mainValue);
        } catch (NumberFormatException ex) {
            isMain = "1".equals(mainValue) ? 1 : 0;
        }
        Box box = null;
        if (isMain == 0 && source.get("box_x1") != null) {
            box = new Box(
                    toFloat(source.get("box_x1")),
                    toFloat(source.get("box_y1")),
                    toFloat(source.get("box_x2")),
                    toFloat(source.get("box_y2")),
                    0, "", 0
            );
        }
        String metaJson = source.get("meta_json") instanceof String ? (String) source.get("meta_json") : null;
        Map<String, String> meta = metaJson != null ? JsonUtils.json2Map(metaJson) : Collections.emptyMap();
        return new LuceHit(
                (String) source.get("image_id"),
                (String) source.get("img_url"),
                box,
                score,
                (String) source.get("camera_id"),
                (String) source.get("group_id"),
                meta
        );
    }

    private static float toFloat(Object value) {
        if (value instanceof Number number) {
            return number.floatValue();
        }
        return value != null ? Float.parseFloat(value.toString()) : 0f;
    }

    private static List<Float> floatArrayToList(float[] source) {
        List<Float> list = new ArrayList<>(source.length);
        for (float v : source) {
            list.add(v);
        }
        return list;
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
        if (boxObj instanceof Box b) {
            box = b;
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


