package com.example.knowledge.rag;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.knowledge.entity.DocumentChunk;
import com.example.knowledge.mapper.DocumentChunkMapper;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Component
public class KeywordRetriever {
    private final DocumentChunkMapper mapper;
    public KeywordRetriever(DocumentChunkMapper mapper) { this.mapper = mapper; }

    public List<VectorStore.VectorHit> search(Long knowledgeBaseId, String question, int limit) {
        List<String> queryTokens = LocalEmbeddingService.tokens(question);
        return mapper.selectList(new QueryWrapper<DocumentChunk>().eq("knowledge_base_id", knowledgeBaseId)
                        .inSql("document_id", "SELECT id FROM document WHERE status='SUCCESS'")).stream()
                .filter(chunk -> knowledgeBaseId.equals(chunk.getKnowledgeBaseId()))
                .map(chunk -> new VectorStore.VectorHit(chunk, keywordScore(chunk, queryTokens)))
                .filter(hit -> hit.score() > 0)
                .sorted(Comparator.comparingDouble(VectorStore.VectorHit::score).reversed()).limit(limit).toList();
    }

    private double keywordScore(DocumentChunk chunk, List<String> queryTokens) {
        if (queryTokens.isEmpty() || chunk.getContent() == null) return 0;
        String content = chunk.getContent().toLowerCase(Locale.ROOT);
        long matches = queryTokens.stream().filter(content::contains).distinct().count();
        return (double) matches / queryTokens.size();
    }
}

