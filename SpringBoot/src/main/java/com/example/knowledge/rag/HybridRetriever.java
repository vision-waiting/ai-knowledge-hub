package com.example.knowledge.rag;

import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.HashSet;
import org.springframework.beans.factory.annotation.Autowired;

@Component
public class HybridRetriever {
    private final VectorStore vectorStore;
    private final KeywordRetriever keywordRetriever;
    private final EmbeddingService embeddingService;
    private final double threshold;
    private final int contextMaxChars;

    public HybridRetriever(VectorStore vectorStore, KeywordRetriever keywordRetriever, EmbeddingService embeddingService,
                           double threshold) {
        this(vectorStore, keywordRetriever, embeddingService, threshold, 12000);
    }

    @Autowired
    public HybridRetriever(VectorStore vectorStore, KeywordRetriever keywordRetriever, EmbeddingService embeddingService,
                           @Value("${knowledge.retrieval-threshold:0.2}") double threshold,
                           @Value("${knowledge.context-max-chars:12000}") int contextMaxChars) {
        if (contextMaxChars < 1) throw new IllegalArgumentException("context-max-chars must be positive");
        this.vectorStore = vectorStore;
        this.keywordRetriever = keywordRetriever;
        this.embeddingService = embeddingService;
        this.threshold = threshold;
        this.contextMaxChars = contextMaxChars;
    }

    public RetrievalResult retrieve(Long knowledgeBaseId, String question, int topK) {
        if (question == null || question.isBlank() || topK < 1) return new RetrievalResult(List.of(), false);
        Map<Long, double[]> scores = new HashMap<>();
        Map<Long, com.example.knowledge.entity.DocumentChunk> chunks = new HashMap<>();
        vectorStore.search(knowledgeBaseId, embeddingService.embed(question), topK * 2).forEach(hit -> {
            if (!eligible(hit, knowledgeBaseId)) return;
            scores.computeIfAbsent(hit.chunk().getId(), ignored -> new double[2])[0] = hit.score(); chunks.put(hit.chunk().getId(), hit.chunk());
        });
        keywordRetriever.search(knowledgeBaseId, question, topK * 2).forEach(hit -> {
            if (!eligible(hit, knowledgeBaseId)) return;
            scores.computeIfAbsent(hit.chunk().getId(), ignored -> new double[2])[1] = hit.score(); chunks.put(hit.chunk().getId(), hit.chunk());
        });
        List<RetrievedChunk> ranked = scores.entrySet().stream().map(entry -> {
            double vector = entry.getValue()[0], keyword = entry.getValue()[1];
            return new RetrievedChunk(chunks.get(entry.getKey()), vector, keyword, 0.7 * vector + 0.3 * keyword);
        }).filter(item -> item.hybridScore() >= Math.max(threshold, .000001))
                .filter(item -> !(embeddingService instanceof LocalEmbeddingService) || item.keywordScore() > 0)
                .sorted(Comparator.comparingDouble(RetrievedChunk::hybridScore).reversed()
                        .thenComparing(item -> item.chunk().getId())).toList();
        List<RetrievedChunk> items = new ArrayList<>();
        var seen = new HashSet<String>();
        int remaining = contextMaxChars;
        for (RetrievedChunk item : ranked) {
            String content = item.chunk().getContent();
            if (content.length() > remaining) continue;
            // Retain complete source chunks so citation text and model evidence stay aligned.
            if (!seen.add(content.replaceAll("\\s+", " ").strip())) continue;
            items.add(item);
            remaining -= content.length();
            if (items.size() == topK) break;
        }
        double best = items.stream().mapToDouble(RetrievedChunk::hybridScore).max().orElse(0);
        return new RetrievalResult(items, !items.isEmpty() && best >= threshold);
    }

    private boolean eligible(VectorStore.VectorHit hit, Long knowledgeBaseId) {
        return hit.chunk() != null && hit.chunk().getId() != null
                && hit.chunk().getContent() != null && !hit.chunk().getContent().isBlank()
                && knowledgeBaseId.equals(hit.chunk().getKnowledgeBaseId()) && Double.isFinite(hit.score()) && hit.score() > 0;
    }
}
