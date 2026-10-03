package com.example.knowledge.rag;

import java.util.List;

public interface VectorStore {
    List<VectorHit> search(Long knowledgeBaseId, List<Float> queryVector, int limit);
    default void upsert(com.example.knowledge.entity.DocumentChunk chunk, List<Float> vector) { }
    default void deleteByDocument(Long documentId) { }
    default void deleteByKnowledgeBase(Long knowledgeBaseId) { }
    record VectorHit(com.example.knowledge.entity.DocumentChunk chunk, double score) {}
}
