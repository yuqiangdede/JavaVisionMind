package com.yuqiangdede.ffe.util;

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

import com.yuqiangdede.common.chroma.ChromaStore;
import com.yuqiangdede.common.chroma.EmbeddingRecord;
import com.yuqiangdede.common.chroma.InMemoryChromaStore;
import com.yuqiangdede.common.chroma.SearchResult;
import com.yuqiangdede.common.util.VectorUtil;
import com.yuqiangdede.common.vector.ElasticsearchClientFactory;
import com.yuqiangdede.common.vector.ElasticsearchConfig;
import com.yuqiangdede.common.vector.VectorStoreMode;
import com.yuqiangdede.ffe.dto.output.FaceInfo4Search;

/**
 * Shared utility that supports Lucene persistence, an in-memory chroma store, or Elasticsearch.
 */
public final class FfeVectorStoreUtil {
    private static final String VECTOR_FIELD = "vector";
    private static final Object LUCENE_LOCK = new Object();
    private static final Object ES_LOCK = new Object();

    private static volatile VectorStoreMode mode = VectorStoreMode.MEMORY;
    private static SearcherManager searcherManager;
    private static IndexWriter writer;
    private static FSDirectory directory;
    private static ChromaStore inMemoryStore;

    private static RestHighLevelClient esClient;
    private static ElasticsearchConfig esConfig;
    private static boolean esIndexReady;

    private FfeVectorStoreUtil() {
    }

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

    public static void add(float[] vector, String imgUrl, String id, String groupId) throws IOException {
        Objects.requireNonNull(vector, "vector");
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(groupId, "groupId");
        switch (mode) {
            case LUCENE -> addToLucene(vector, imgUrl, id, groupId);
            case MEMORY -> addToMemory(vector, imgUrl, id, groupId);
            case ELASTICSEARCH -> addToElasticsearch(vector, imgUrl, id, groupId);
            default -> throw new IllegalStateException("Unsupported vector store mode: " + mode);
        }
    }

    public static void delete(String id) throws IOException {
        switch (mode) {
            case LUCENE -> {
                synchronized (LUCENE_LOCK) {
                    if (writer != null) {
                        writer.deleteDocuments(new Term("id", id));
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

    public static List<FaceInfo4Search> searchTop(float[] queryVector, String groupId, float confThreshold, int n) throws IOException {
        return switch (mode) {
            case LUCENE -> searchWithLucene(queryVector, groupId, confThreshold, n);
            case MEMORY -> searchInMemory(queryVector, groupId, confThreshold, n);
            case ELASTICSEARCH -> searchWithElasticsearch(queryVector, groupId, confThreshold, n);
            default -> throw new IllegalStateException("Unsupported vector store mode: " + mode);
        };
    }

    private static void addToLucene(float[] vector, String imgUrl, String id, String groupId) throws IOException {
        Document doc = new Document();
        doc.add(new KnnFloatVectorField(VECTOR_FIELD, VectorUtil.normalizeVector(vector)));
        long time = System.currentTimeMillis();
        doc.add(new LongPoint("time", time));
        doc.add(new StoredField("time_stored", time));
        doc.add(new StringField("groupId", groupId, Field.Store.YES));
        doc.add(new StringField("id", id, Field.Store.YES));
        doc.add(new StringField("imgUrl", imgUrl, Field.Store.YES));
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

    private static void addToElasticsearch(float[] vector, String imgUrl, String id, String groupId) throws IOException {
        ensureEsReady(vector.length);
        Map<String, Object> doc = new HashMap<>();
        doc.put("id", id);
        doc.put("groupId", groupId);
        doc.put("imgUrl", imgUrl);
        doc.put("time", System.currentTimeMillis());
        doc.put(VECTOR_FIELD, floatArrayToList(VectorUtil.normalizeVector(vector)));
        IndexRequest request = new IndexRequest(esConfig.getIndex())
                .id(id)
                .source(doc)
                .setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);
        esClient.index(request, RequestOptions.DEFAULT);
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
            KnnFloatVectorQuery knnQuery = new KnnFloatVectorQuery(VECTOR_FIELD, VectorUtil.normalizeVector(queryVector), Math.max(limit, 1));
            finalQueryBuilder.add(knnQuery, BooleanClause.Occur.MUST);
            if (groupId != null) {
                Query resourceQuery = new TermQuery(new Term("groupId", groupId));
                finalQueryBuilder.add(resourceQuery, BooleanClause.Occur.FILTER);
            }
            TopDocs topDocs = searcher.search(finalQueryBuilder.build(), limit);
            StoredFields storedFields = searcher.storedFields();
            Set<String> fields = new HashSet<>();
            Collections.addAll(fields, "id", "imgUrl");
            for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
                if (scoreDoc.score < confThreshold) {
                    continue;
                }
                Document doc = storedFields.document(scoreDoc.doc, fields);
                String hitId = doc.get("id");
                String imgUrl = doc.get("imgUrl");
                face4Search.add(new FaceInfo4Search(hitId, imgUrl, scoreDoc.score));
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

    private static List<FaceInfo4Search> searchWithElasticsearch(float[] queryVector, String groupId, float confThreshold, int n) throws IOException {
        if (esClient == null || esConfig == null) {
            return List.of();
        }
        int limit = Math.max(n, 0);
        if (limit == 0) {
            return List.of();
        }
        ensureEsReady(queryVector.length);
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        sourceBuilder.size(limit);
        float[] normalized = VectorUtil.normalizeVector(queryVector);
        Map<String, Object> params = new HashMap<>();
        params.put("query_vector", floatArrayToList(normalized));
        Script script = new Script(ScriptType.INLINE, "painless",
                "double cosine = cosineSimilarity(params.query_vector, '" + VECTOR_FIELD + "'); return (cosine + 1.0) / 2.0;",
                params);
        QueryBuilder baseQuery;
        if (groupId != null) {
            BoolQueryBuilder boolQuery = QueryBuilders.boolQuery().filter(QueryBuilders.termQuery("groupId", groupId));
            baseQuery = boolQuery.hasClauses() ? boolQuery : QueryBuilders.matchAllQuery();
        } else {
            baseQuery = QueryBuilders.matchAllQuery();
        }
        ScriptScoreFunctionBuilder scriptFunction = new ScriptScoreFunctionBuilder(script);
        FunctionScoreQueryBuilder query = QueryBuilders.functionScoreQuery(baseQuery, scriptFunction);
        sourceBuilder.query(query);
        if (confThreshold > 0) {
            sourceBuilder.minScore(confThreshold);
        }
        SearchRequest request = new SearchRequest(esConfig.getIndex());
        request.source(sourceBuilder);
        SearchResponse response = esClient.search(request, RequestOptions.DEFAULT);
        List<FaceInfo4Search> results = new ArrayList<>();
        for (SearchHit hit : response.getHits().getHits()) {
            float score = hit.getScore();
            if (score < confThreshold) {
                continue;
            }
            Map<String, Object> source = hit.getSourceAsMap();
            String hitId = (String) source.getOrDefault("id", hit.getId());
            Object imgUrlObj = source.get("imgUrl");
            String imgUrl = imgUrlObj instanceof String ? (String) imgUrlObj : null;
            results.add(new FaceInfo4Search(hitId, imgUrl, score));
        }
        return results;
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
            GetIndexRequest existsRequest = new GetIndexRequest(esConfig.getIndex());
            boolean exists = esClient.indices().exists(existsRequest, RequestOptions.DEFAULT);
            if (!exists) {
                CreateIndexRequest createRequest = new CreateIndexRequest(esConfig.getIndex());
                Map<String, Object> vectorMapping = new HashMap<>();
                vectorMapping.put("type", "dense_vector");
                vectorMapping.put("dims", dims);
                vectorMapping.put("index", true);
                vectorMapping.put("similarity", "cosine");

                Map<String, Object> properties = new HashMap<>();
                properties.put(VECTOR_FIELD, vectorMapping);
                properties.put("groupId", Map.of("type", "keyword"));
                properties.put("id", Map.of("type", "keyword"));
                properties.put("imgUrl", Map.of("type", "keyword"));
                properties.put("time", Map.of("type", "date"));

                Map<String, Object> mappings = Map.of("properties", properties);
                createRequest.mapping(mappings);
                esClient.indices().create(createRequest, RequestOptions.DEFAULT);
            }
            esIndexReady = true;
        }
    }

    private static List<Float> floatArrayToList(float[] source) {
        List<Float> list = new ArrayList<>(source.length);
        for (float value : source) {
            list.add(value);
        }
        return list;
    }
}
