package com.example.knowledge.rag;

import com.example.knowledge.entity.DocumentChunk;

public record RetrievedChunk(DocumentChunk chunk, double vectorScore, double keywordScore, double hybridScore) {
}

