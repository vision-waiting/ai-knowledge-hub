package com.example.knowledge.rag;

import com.example.knowledge.entity.DocumentChunk;
import com.example.knowledge.mapper.DocumentMapper;
import org.springframework.stereotype.Component;

@Component
public class CitationBuilder {
    private final DocumentMapper documentMapper;
    public CitationBuilder(DocumentMapper documentMapper) { this.documentMapper = documentMapper; }

    public Citation build(DocumentChunk chunk, int index) {
        var document = documentMapper.selectById(chunk.getDocumentId());
        String excerpt = chunk.getContent() == null ? "" : (chunk.getContent().length() > 180 ? chunk.getContent().substring(0, 180) + "…" : chunk.getContent());
        return new Citation(index, chunk.getDocumentId(), document == null ? "未知文档" : document.getFileName(), chunk.getPageNumber(), chunk.getId(), excerpt);
    }
}
