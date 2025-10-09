package com.yuqiangdede.reid.util;

import com.yuqiangdede.common.chroma.ChromaStore;
import com.yuqiangdede.common.chroma.EmbeddingRecord;
import com.yuqiangdede.common.chroma.InMemoryChromaStore;
import com.yuqiangdede.common.chroma.SearchResult;
import com.yuqiangdede.common.util.RandomProjectionUtils;
import com.yuqiangdede.common.util.VectorUtil;
import com.yuqiangdede.common.vector.ElasticsearchClientFactory;
import com.yuqiangdede.common.vector.ElasticsearchConfig;
import com.yuqiangdede.common.vector.VectorStoreMode;
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
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.KnnFloatVectorQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.SearcherFactory;
import org.apache.lucene.search.SearcherManager;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.FSDirectory;
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

import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Vector storage helper for ReID supporting Lucene persistence, an in-memory store, or Elasticsearch.
 */
public final class ReidVectorStoreUtil {
    private static final String VECTOR_FIELD = "vector";
    private static final Object LUCENE_LOCK = new Object();
    private static final Object ES_LOCK = new Object();

    private static volatile VectorStoreMode mode = VectorStoreMode.MEMORY;
    private static SearcherManager searcherManager;
    private static IndexWriter writer;
    private static FSDirectory directory;
    private static ChromaStore inMemoryStore;

    @SuppressWarnings("deprecation")
    private static RestHighLevelClient esClient;
    private static ElasticsearchConfig esConfig;
    private static boolean esIndexReady;

    private ReidVectorStoreUtil() {
    }

    /**
     * Initialise storage layer.
     *
     * @param indexPath   location for Lucene indices
     * @param storeMode   selected vector store mode
     * @param config      Elasticsearch configuration (required when mode is ELASTICSEARCH)
     */
    public static void init(String indexPath, VectorStoreMode storeMode, ElasticsearchConfig config) throws Exception {
        close();
        mode = storeMode == null ? VectorStoreMode.LUCENE : storeMode;
        switch (mode) {
            case LUCENE -> initLucene(indexPath);
            case MEMORY -> inMemoryStore = new InMemoryChromaStore();
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

    public static void close() throws Exception {
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
        if (esClient != null) {
            esClient.close();
            esClient = null;
        }
        esConfig = null;
        esIndexReady = false;
    }

    public static void add(String imgUrl, String cameraId, String humanId, Feature feature) {
        Objects.requireNonNull(feature, "feature");
        switch (mode) {
            case LUCENE -> addToLucene(imgUrl, cameraId, humanId, feature);
            case MEMORY -> addToMemory(imgUrl, cameraId, humanId, feature);
            case ELASTICSEARCH -> addToElasticsearch(imgUrl, cameraId, humanId, feature);
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
                    inMemoryStore.delete(id);
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
            }
            case ELASTICSEARCH -> deleteAllFromElasticsearch();
            default -> throw new IllegalStateException("Unsupported vector store mode: " + mode);
        }
    }

    public static List<Human> searchByVector(float[] vec, String cameraId, Integer topN, float confThreshold) {
        return switch (mode) {
            case LUCENE -> searchWithLucene(projectForLucene(vec), cameraId, topN, confThreshold);
            case MEMORY -> searchInMemory(vec, cameraId, topN, confThreshold);
            case ELASTICSEARCH -> searchWithElasticsearch(vec, cameraId, topN, confThreshold);
            default -> throw new IllegalStateException("Unsupported vector store mode: " + mode);
        };
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
                if (writer == null) {
                    throw new IllegalStateException("Lucene index writer is not initialised.");
                }
                writer.addDocument(doc);
                writer.commit();
                if (searcherManager != null) {
                    searcherManager.maybeRefresh();
                }
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

    private static void addToElasticsearch(String imgUrl, String cameraId, String humanId, Feature feature) {
        try {
            ensureEsReady(feature.getEmbeds().length);
            String resolvedHumanId = humanId != null ? humanId : feature.getUuid();
            Map<String, Object> document = new HashMap<>();
            document.put("image_id", feature.getUuid());
            document.put("human_id", resolvedHumanId);
            if (cameraId != null) {
                document.put("camera_id", cameraId);
            }
            if (imgUrl != null) {
                document.put("img_url", imgUrl);
            }
            document.put("timestamp", System.currentTimeMillis());
            document.put(VECTOR_FIELD, floatArrayToList(VectorUtil.normalizeVector(feature.getEmbeds())));

            IndexRequest request = new IndexRequest(esConfig.getIndex())
                    .id(feature.getUuid())
                    .source(document)
                    .setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);
            esClient.index(request, RequestOptions.DEFAULT);
        } catch (IOException e) {
            throw new RuntimeException("Failed to persist vector to Elasticsearch", e);
        }
    }

    private static void deleteFromElasticsearch(String id) throws IOException {
        if (esClient == null || esConfig == null) {
            return;
        }
        DeleteRequest request = new DeleteRequest(esConfig.getIndex(), id)
                .setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);
        esClient.delete(request, RequestOptions.DEFAULT);
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

    private static List<Human> searchWithElasticsearch(float[] vec, String cameraId, Integer topN, float confThreshold) {
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
            QueryBuilder baseQuery;
            if (cameraId != null) {
                BoolQueryBuilder bool = QueryBuilders.boolQuery().filter(QueryBuilders.termQuery("camera_id", cameraId));
                baseQuery = bool.hasClauses() ? bool : QueryBuilders.matchAllQuery();
            } else {
                baseQuery = QueryBuilders.matchAllQuery();
            }
            ScriptScoreFunctionBuilder scriptFunction = new ScriptScoreFunctionBuilder(script);
            FunctionScoreQueryBuilder query = QueryBuilders.functionScoreQuery(baseQuery, scriptFunction);
            if (confThreshold > 0) {
                sourceBuilder.minScore(confThreshold);
            }
            sourceBuilder.query(query);

            SearchRequest request = new SearchRequest(esConfig.getIndex());
            request.source(sourceBuilder);
            SearchResponse response = esClient.search(request, RequestOptions.DEFAULT);
            List<Human> results = new ArrayList<>();
            for (SearchHit hit : response.getHits().getHits()) {
                float score = hit.getScore();
                if (score < confThreshold) {
                    continue;
                }
                Map<String, Object> source = hit.getSourceAsMap();
                String humanId = (String) source.getOrDefault("human_id", hit.getId());
                String imageId = (String) source.getOrDefault("image_id", hit.getId());
                Object imgUrlObj = source.get("img_url");
                String imgUrl = imgUrlObj instanceof String ? (String) imgUrlObj : null;
                String camera = (String) source.get("camera_id");
                results.add(new Human(humanId, imageId, imgUrl, score, camera, "exist"));
            }
            return results;
        } catch (IOException e) {
            throw new RuntimeException("Elasticsearch search failed", e);
        }
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
                properties.put("human_id", Map.of("type", "keyword"));
                properties.put("camera_id", Map.of("type", "keyword"));
                properties.put("img_url", Map.of("type", "keyword"));
                properties.put("timestamp", Map.of("type", "date"));

                Map<String, Object> mappings = Map.of("properties", properties);
                create.mapping(mappings);
                esClient.indices().create(create, RequestOptions.DEFAULT);
            }
            esIndexReady = true;
        }
    }

    private static List<Float> floatArrayToList(float[] source) {
        List<Float> list = new ArrayList<>(source.length);
        for (float v : source) {
            list.add(v);
        }
        return list;
    }
}
