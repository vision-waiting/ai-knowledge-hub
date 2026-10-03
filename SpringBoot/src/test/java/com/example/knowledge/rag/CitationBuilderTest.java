package com.example.knowledge.rag;

import com.example.knowledge.entity.Document;
import com.example.knowledge.entity.DocumentChunk;
import com.example.knowledge.mapper.DocumentMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CitationBuilderTest {
    @Test
    void citationContainsDocumentNamePageAndChunk() {
        DocumentMapper mapper = mock(DocumentMapper.class);
        Document document = new Document(); document.setId(12L); document.setFileName("Java.pdf");
        when(mapper.selectById(12L)).thenReturn(document);
        DocumentChunk chunk = new DocumentChunk(); chunk.setId(1001L); chunk.setDocumentId(12L); chunk.setPageNumber(8); chunk.setContent("HashMap content");

        Citation citation = new CitationBuilder(mapper).build(chunk, 1);

        assertEquals("Java.pdf", citation.documentName());
        assertEquals(8, citation.pageNumber());
        assertEquals(1001L, citation.chunkId());
    }
}

