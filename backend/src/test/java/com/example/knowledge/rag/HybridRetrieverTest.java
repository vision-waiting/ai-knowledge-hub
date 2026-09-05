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
    void hybridScoreCombinesVectorAndKeywordAndKeepsKnowledgeBaseIsolation() {
        DocumentChunk first = chunk(1L, 3L, "HashMap uses an array and red black tree");
        DocumentChunk second = chunk(2L, 9L, "Redis stores strings and lists");
        DocumentChunkMapper mapper = mock(DocumentChunkMapper.class);
        when(mapper.selectList(any())).thenReturn(List.of(first, second));
        HybridRetriever retriever = new HybridRetriever(new LocalVectorStore(mapper, new LocalEmbeddingService()), new KeywordRetriever(mapper), new LocalEmbeddingService(), 0.1);

        RetrievalResult result = retriever.retrieve(3L, "HashMap array", 2);

        assertEquals(1L, result.items().get(0).chunk().getId());
        assertTrue(result.sufficient());
    }

    private DocumentChunk chunk(Long id, Long knowledgeBaseId, String content) {
        DocumentChunk chunk = new DocumentChunk(); chunk.setId(id); chunk.setKnowledgeBaseId(knowledgeBaseId); chunk.setContent(content); chunk.setPageNumber(2); return chunk;
    }
}

