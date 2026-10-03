package com.example.knowledge.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.example.knowledge.entity.DocumentChunk;
import com.example.knowledge.mapper.DocumentChunkMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

@Component
@ConditionalOnProperty(name = "knowledge.qdrant-enabled", havingValue = "true")
public class QdrantVectorStore implements VectorStore {
    private static final String COLLECTION = "/collections/knowledge_chunks";
    private final RestClient client;
    private final DocumentChunkMapper mapper;
    @org.springframework.beans.factory.annotation.Autowired
    private com.example.knowledge.mapper.DocumentMapper documents;
    private volatile int collectionDimension;

    public QdrantVectorStore(@Value("${knowledge.qdrant-url:http://localhost:6333}") String url, DocumentChunkMapper mapper) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout(30_000);
        this.client = RestClient.builder().baseUrl(url).requestFactory(factory).build(); this.mapper = mapper;
    }

    @Override
    public List<VectorHit> search(Long knowledgeBaseId, List<Float> queryVector, int limit) {
        validateVector(queryVector);
        if (limit <= 0) return List.of();
        Map<String, Object> scope = filter("knowledgeBaseId", knowledgeBaseId);
        if (documents != null) {
            var ids = documents.selectList(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<com.example.knowledge.entity.Document>()
                    .select("id").eq("knowledge_base_id", knowledgeBaseId).eq("status", "SUCCESS")).stream().map(com.example.knowledge.entity.Document::getId).toList();
            if (ids.isEmpty()) return List.of();
            scope = Map.of("must", List.of(Map.of("key", "knowledgeBaseId", "match", Map.of("value", knowledgeBaseId)),
                    Map.of("key", "documentId", "match", Map.of("any", ids))));
        }
        ensureCollection(queryVector.size());
        JsonNode root = client.post().uri(COLLECTION + "/points/search").body(Map.of("vector", queryVector, "limit", Math.min(limit, 100), "with_payload", true, "filter", scope)).retrieve().body(JsonNode.class);
        List<VectorHit> result = new ArrayList<>();
        if (root != null && root.path("result").isArray()) root.path("result").forEach(item -> {
            long chunkId = item.path("payload").path("chunkId").asLong(0); DocumentChunk chunk = mapper.selectById(chunkId);
            if (chunk == null || !knowledgeBaseId.equals(chunk.getKnowledgeBaseId())) return;
            if (documents != null) {
                var document = documents.selectById(chunk.getDocumentId());
                if (document == null || !"SUCCESS".equals(document.getStatus())) return;
            }
            result.add(new VectorHit(chunk, item.path("score").asDouble(0)));
        });
        return result;
    }

    @Override
    public void upsert(DocumentChunk chunk, List<Float> vector) {
        validateVector(vector);
        ensureCollection(vector.size());
        Map<String, Object> payload = new HashMap<>();
        payload.put("chunkId", chunk.getId());
        payload.put("documentId", chunk.getDocumentId());
        payload.put("knowledgeBaseId", chunk.getKnowledgeBaseId());
        if (chunk.getPageNumber() != null) payload.put("pageNumber", chunk.getPageNumber());
        client.put().uri(COLLECTION + "/points?wait=true").body(Map.of("points", List.of(Map.of("id", chunk.getId(), "vector", vector, "payload", payload)))).retrieve().toBodilessEntity();
    }

    @Override
    public void deleteByDocument(Long documentId) {
        deleteByFilter("documentId", documentId);
    }

    @Override
    public void deleteByKnowledgeBase(Long knowledgeBaseId) {
        deleteByFilter("knowledgeBaseId", knowledgeBaseId);
    }

    private void deleteByFilter(String key, Long value) {
        try {
            client.post().uri(COLLECTION + "/points/delete?wait=true").body(Map.of("filter", filter(key, value)))
                    .retrieve().toBodilessEntity();
        } catch (HttpClientErrorException.NotFound ignored) { collectionDimension = 0; }
    }

    private Map<String, Object> filter(String key, Long value) {
        return Map.of("must", List.of(Map.of("key", key, "match", Map.of("value", value))));
    }

    private synchronized void ensureCollection(int dimension) {
        if (collectionDimension != 0) { requireDimension(collectionDimension, dimension); return; }
        try {
            JsonNode collection = client.get().uri(COLLECTION).retrieve().body(JsonNode.class);
            int existing = collection == null ? 0 : collection.path("result").path("config").path("params").path("vectors").path("size").asInt();
            requireDimension(existing, dimension);
        } catch (HttpClientErrorException.NotFound missing) {
            try {
                client.put().uri(COLLECTION).body(Map.of("vectors", Map.of("size", dimension, "distance", "Cosine")))
                        .retrieve().toBodilessEntity();
            } catch (HttpClientErrorException.Conflict createdConcurrently) {
                JsonNode collection = client.get().uri(COLLECTION).retrieve().body(JsonNode.class);
                requireDimension(collection.path("result").path("config").path("params").path("vectors").path("size").asInt(), dimension);
            }
        }
        collectionDimension = dimension;
    }

    private void requireDimension(int existing, int expected) {
        if (existing != expected) throw new IllegalStateException("Qdrant 向量维度为 " + existing + "，当前 Embedding 维度为 " + expected + "；请迁移集合后重新索引");
    }

    private void validateVector(List<Float> vector) {
        if (vector == null || vector.isEmpty() || vector.stream().anyMatch(value -> value == null || !Float.isFinite(value)))
            throw new IllegalArgumentException("Embedding 必须是非空有限数值向量");
    }
}
