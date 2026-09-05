package com.example.knowledge.rag;

import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class HybridRetriever {
    private final VectorStore vectorStore;
    private final KeywordRetriever keywordRetriever;
    private final EmbeddingService embeddingService;
    private final double threshold;

    public HybridRetriever(VectorStore vectorStore, KeywordRetriever keywordRetriever, EmbeddingService embeddingService,
                           @Value("${knowledge.retrieval-threshold:0.2}") double threshold) { this.vectorStore = vectorStore; this.keywordRetriever = keywordRetriever; this.embeddingService = embeddingService; this.threshold = threshold; }

    public RetrievalResult retrieve(Long knowledgeBaseId, String question, int topK) {
        Map<Long, double[]> scores = new HashMap<>();
        Map<Long, com.example.knowledge.entity.DocumentChunk> chunks = new HashMap<>();
        vectorStore.search(knowledgeBaseId, embeddingService.embed(question), topK * 2).forEach(hit -> {
            scores.computeIfAbsent(hit.chunk().getId(), ignored -> new double[2])[0] = hit.score(); chunks.put(hit.chunk().getId(), hit.chunk());
        });
        keywordRetriever.search(knowledgeBaseId, question, topK * 2).forEach(hit -> {
            scores.computeIfAbsent(hit.chunk().getId(), ignored -> new double[2])[1] = hit.score(); chunks.put(hit.chunk().getId(), hit.chunk());
        });
        List<RetrievedChunk> items = scores.entrySet().stream().map(entry -> {
            double vector = entry.getValue()[0], keyword = entry.getValue()[1];
            return new RetrievedChunk(chunks.get(entry.getKey()), vector, keyword, 0.7 * vector + 0.3 * keyword);
        }).sorted(Comparator.comparingDouble(RetrievedChunk::hybridScore).reversed()).limit(topK).toList();
        double best = items.stream().mapToDouble(RetrievedChunk::hybridScore).max().orElse(0);
        return new RetrievalResult(items, best >= threshold);
    }
}
