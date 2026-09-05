package com.example.knowledge.rag;

import java.util.List;

public interface VectorStore {
    List<VectorHit> search(Long knowledgeBaseId, List<Float> queryVector, int limit);
    record VectorHit(com.example.knowledge.entity.DocumentChunk chunk, double score) {}
}

