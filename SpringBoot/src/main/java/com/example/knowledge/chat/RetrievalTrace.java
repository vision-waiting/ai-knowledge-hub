package com.example.knowledge.chat;

import com.example.knowledge.rag.RetrievalResult;
import java.util.List;

/** What retrieval actually fed into an answer, kept so users can inspect why an answer looks the way it does. */
public record RetrievalTrace(String rewrittenQuery, int topK, boolean sufficient, long latencyMs, List<TraceChunk> chunks) {
    public record TraceChunk(Long chunkId, Long documentId, Integer pageNumber, double vectorScore, double keywordScore, double hybridScore) {}
    public static RetrievalTrace of(String rewrittenQuery, int topK, RetrievalResult result, long latencyMs) {
        return new RetrievalTrace(rewrittenQuery, topK, result.sufficient(), latencyMs, result.items().stream()
                .map(item -> new TraceChunk(item.chunk().getId(), item.chunk().getDocumentId(), item.chunk().getPageNumber(),
                        item.vectorScore(), item.keywordScore(), item.hybridScore())).toList());
    }
}
