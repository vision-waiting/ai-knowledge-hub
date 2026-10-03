package com.example.knowledge.rag;

import com.example.knowledge.entity.DocumentChunk;
import com.example.knowledge.mapper.DocumentChunkMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HybridRetrieverTest {
    @Test
    void customBudgetSkipsNonFittingChunksAndIncludesLaterExactFit() {
        VectorStore vectors = mock(VectorStore.class);
        KeywordRetriever keywords = mock(KeywordRetriever.class);
        EmbeddingService embeddings = mock(EmbeddingService.class);
        when(embeddings.embed("query")).thenReturn(List.of(1f));
        when(vectors.search(3L, List.of(1f), 6)).thenReturn(List.of(
                new VectorStore.VectorHit(chunk(1L, 3L, "abcdef"), .95),
                new VectorStore.VectorHit(chunk(2L, 3L, "ghijk"), .9),
                new VectorStore.VectorHit(chunk(3L, 3L, "lmno"), .8)));
        when(keywords.search(3L, "query", 6)).thenReturn(List.of());
        var result = new HybridRetriever(vectors, keywords, embeddings, .2, 10).retrieve(3L, "query", 3);
        assertEquals(List.of(1L, 3L), result.items().stream().map(item -> item.chunk().getId()).toList());
        assertEquals(List.of("abcdef", "lmno"), result.items().stream().map(item -> item.chunk().getContent()).toList());
        assertEquals(10, result.items().stream().mapToInt(item -> item.chunk().getContent().length()).sum());
        var insufficient = new HybridRetriever(vectors, keywords, embeddings, .2, 3).retrieve(3L, "query", 3);
        assertTrue(insufficient.items().isEmpty());
        org.junit.jupiter.api.Assertions.assertFalse(insufficient.sufficient());
    }

    @Test
    void duplicateAndBlankChunksDoNotConsumeTopK() {
        VectorStore vectors = mock(VectorStore.class);
        KeywordRetriever keywords = mock(KeywordRetriever.class);
        EmbeddingService embeddings = mock(EmbeddingService.class);
        when(embeddings.embed("query")).thenReturn(List.of(1f));
        when(vectors.search(3L, List.of(1f), 4)).thenReturn(List.of(
                new VectorStore.VectorHit(chunk(1L, 3L, "first evidence"), .95),
                new VectorStore.VectorHit(chunk(2L, 3L, "first  evidence"), .9),
                new VectorStore.VectorHit(chunk(3L, 3L, "  "), .85),
                new VectorStore.VectorHit(chunk(4L, 3L, "second evidence"), .8)));
        when(keywords.search(3L, "query", 4)).thenReturn(List.of());
        var result = new HybridRetriever(vectors, keywords, embeddings, .2).retrieve(3L, "query", 2);
        assertEquals(List.of(1L, 4L), result.items().stream().map(item -> item.chunk().getId()).toList());
    }

    @Test
    void oversizedEvidenceIsSkippedWithoutTruncatingSources() {
        VectorStore vectors = mock(VectorStore.class);
        KeywordRetriever keywords = mock(KeywordRetriever.class);
        EmbeddingService embeddings = mock(EmbeddingService.class);
        when(embeddings.embed("query")).thenReturn(List.of(1f));
        when(vectors.search(3L, List.of(1f), 4)).thenReturn(List.of(
                new VectorStore.VectorHit(chunk(1L, 3L, "x".repeat(13000)), .95),
                new VectorStore.VectorHit(chunk(2L, 3L, "small evidence"), .8)));
        when(keywords.search(3L, "query", 4)).thenReturn(List.of());
        var result = new HybridRetriever(vectors, keywords, embeddings, .2).retrieve(3L, "query", 2);
        assertEquals(List.of(2L), result.items().stream().map(item -> item.chunk().getId()).toList());
        assertTrue(result.sufficient());
    }

    @Test
    void hybridScoreCombinesVectorAndKeywordAndKeepsKnowledgeBaseIsolation() {
        DocumentChunk first = chunk(1L, 3L, "HashMap uses an array and red black tree");
        DocumentChunk second = chunk(2L, 9L, "Redis stores strings and lists");
        DocumentChunkMapper mapper = mock(DocumentChunkMapper.class);
        when(mapper.selectList(any())).thenReturn(List.of(first, second));
        HybridRetriever retriever = new HybridRetriever(new LocalVectorStore(mapper, new LocalEmbeddingService()), new KeywordRetriever(mapper), new LocalEmbeddingService(), 0.1);

        RetrievalResult result = retriever.retrieve(3L, "HashMap array", 2);

        assertEquals(1L, result.items().get(0).chunk().getId());
        assertTrue(result.sufficient());
        assertTrue(result.items().stream().allMatch(item -> item.chunk().getKnowledgeBaseId().equals(3L)));
    }

    @Test
    void zeroAndBelowThresholdChunksAreExcludedFromContext() {
        VectorStore vectors = mock(VectorStore.class);
        KeywordRetriever keywords = mock(KeywordRetriever.class);
        EmbeddingService embeddings = mock(EmbeddingService.class);
        when(embeddings.embed("relevant")).thenReturn(List.of(1f));
        when(vectors.search(3L, List.of(1f), 6)).thenReturn(List.of(
                new VectorStore.VectorHit(chunk(1L, 3L, "relevant"), .9),
                new VectorStore.VectorHit(chunk(2L, 3L, "unrelated"), .01),
                new VectorStore.VectorHit(chunk(3L, 3L, "zero"), 0)));
        when(keywords.search(3L, "relevant", 6)).thenReturn(List.of());
        RetrievalResult result = new HybridRetriever(vectors, keywords, embeddings, .2).retrieve(3L, "relevant", 3);
        assertEquals(List.of(1L), result.items().stream().map(item -> item.chunk().getId()).toList());
    }

    @Test
    void noRelevantResultsMeansNoContextEvenWithHashCollisions() {
        DocumentChunkMapper mapper = mock(DocumentChunkMapper.class);
        when(mapper.selectList(any())).thenReturn(List.of(chunk(1L, 3L, "数据库索引优化方法")));
        LocalEmbeddingService embeddings = new LocalEmbeddingService();
        RetrievalResult result = new HybridRetriever(new LocalVectorStore(mapper, embeddings), new KeywordRetriever(mapper), embeddings, .2)
                .retrieve(3L, "今天天气如何", 5);
        assertTrue(result.items().isEmpty());
        org.junit.jupiter.api.Assertions.assertFalse(result.sufficient());
    }

    private DocumentChunk chunk(Long id, Long knowledgeBaseId, String content) {
        DocumentChunk chunk = new DocumentChunk(); chunk.setId(id); chunk.setKnowledgeBaseId(knowledgeBaseId); chunk.setContent(content); chunk.setPageNumber(2); return chunk;
    }
}
