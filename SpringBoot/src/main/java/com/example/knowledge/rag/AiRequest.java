package com.example.knowledge.rag;

import java.util.List;

public record AiRequest(String question, List<RetrievedChunk> context, boolean sufficient, List<ConversationMessage> history) {
    public AiRequest(String question, List<RetrievedChunk> context, boolean sufficient) { this(question, context, sufficient, List.of()); }
    public AiRequest {
        context = context == null ? List.of() : List.copyOf(context);
        history = history == null ? List.of() : List.copyOf(history);
    }
}
