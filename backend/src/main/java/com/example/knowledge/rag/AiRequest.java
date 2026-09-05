package com.example.knowledge.rag;

import java.util.List;

public record AiRequest(String question, List<RetrievedChunk> context, boolean sufficient) {
    public AiRequest { context = context == null ? List.of() : List.copyOf(context); }
}

