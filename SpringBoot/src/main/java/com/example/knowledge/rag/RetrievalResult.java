package com.example.knowledge.rag;

import java.util.List;

public record RetrievalResult(List<RetrievedChunk> items, boolean sufficient) {
    public RetrievalResult { items = items == null ? List.of() : List.copyOf(items); }
}

