package com.example.knowledge.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.example.knowledge.entity.DocumentChunk;
import com.example.knowledge.mapper.DocumentChunkMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@ConditionalOnProperty(name = "knowledge.qdrant-enabled", havingValue = "true")
public class QdrantVectorStore implements VectorStore {
    private final RestClient client;
    private final DocumentChunkMapper mapper;

    public QdrantVectorStore(@Value("${knowledge.qdrant-url:http://localhost:6333}") String url, DocumentChunkMapper mapper) {
        this.client = RestClient.builder().baseUrl(url).build(); this.mapper = mapper;
    }

    @Override
    public List<VectorHit> search(Long knowledgeBaseId, List<Float> queryVector, int limit) {
        JsonNode root = client.post().uri("/collections/knowledge_chunks/points/search").body(Map.of("vector", queryVector, "limit", limit, "with_payload", true, "filter", Map.of("must", List.of(Map.of("key", "knowledgeBaseId", "match", Map.of("value", knowledgeBaseId)))))).retrieve().body(JsonNode.class);
        List<VectorHit> result = new ArrayList<>();
        if (root != null && root.path("result").isArray()) root.path("result").forEach(item -> {
            long chunkId = item.path("payload").path("chunkId").asLong(0); DocumentChunk chunk = mapper.selectById(chunkId);
            if (chunk != null) result.add(new VectorHit(chunk, item.path("score").asDouble(0)));
        });
        return result;
    }

    @Override
    public void upsert(DocumentChunk chunk, List<Float> vector) {
        client.put().uri("/collections/knowledge_chunks/points?wait=true").body(Map.of("points", List.of(Map.of("id", chunk.getId(), "vector", vector, "payload", Map.of("chunkId", chunk.getId(), "documentId", chunk.getDocumentId(), "knowledgeBaseId", chunk.getKnowledgeBaseId(), "pageNumber", chunk.getPageNumber(), "content", chunk.getContent()))))).retrieve().toBodilessEntity();
    }

    @Override
    public void deleteByDocument(Long documentId) {
        client.post().uri("/collections/knowledge_chunks/points/delete?wait=true").body(Map.of("filter", Map.of("must", List.of(Map.of("key", "documentId", "match", Map.of("value", documentId)))))).retrieve().toBodilessEntity();
    }

    @Override
    public void deleteByKnowledgeBase(Long knowledgeBaseId) {
        client.post().uri("/collections/knowledge_chunks/points/delete?wait=true").body(Map.of("filter", Map.of("must", List.of(Map.of("key", "knowledgeBaseId", "match", Map.of("value", knowledgeBaseId)))))).retrieve().toBodilessEntity();
    }
}
