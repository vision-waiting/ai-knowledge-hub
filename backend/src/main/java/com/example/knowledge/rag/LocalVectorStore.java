package com.example.knowledge.rag;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.knowledge.entity.DocumentChunk;
import com.example.knowledge.mapper.DocumentChunkMapper;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.Comparator;
import java.util.List;

@Component
@ConditionalOnProperty(name = "knowledge.qdrant-enabled", havingValue = "false", matchIfMissing = true)
public class LocalVectorStore implements VectorStore {
    private final DocumentChunkMapper mapper;
    private final EmbeddingService embeddingService;

    public LocalVectorStore(DocumentChunkMapper mapper, EmbeddingService embeddingService) { this.mapper = mapper; this.embeddingService = embeddingService; }

    @Override
    public List<VectorHit> search(Long knowledgeBaseId, List<Float> queryVector, int limit) {
        return mapper.selectList(new QueryWrapper<DocumentChunk>().eq("knowledge_base_id", knowledgeBaseId)).stream()
                .map(chunk -> new VectorHit(chunk, cosine(queryVector, embeddingService.embed(chunk.getContent()))))
                .sorted(Comparator.comparingDouble(VectorHit::score).reversed()).limit(limit).toList();
    }

    static double cosine(List<Float> left, List<Float> right) {
        double dot = 0, leftNorm = 0, rightNorm = 0;
        for (int index = 0; index < Math.min(left.size(), right.size()); index++) {
            dot += left.get(index) * right.get(index); leftNorm += left.get(index) * left.get(index); rightNorm += right.get(index) * right.get(index);
        }
        return leftNorm == 0 || rightNorm == 0 ? 0 : Math.max(0, dot / Math.sqrt(leftNorm * rightNorm));
    }
}
