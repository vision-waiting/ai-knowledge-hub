package com.example.knowledge.chat;

import com.example.knowledge.rag.Citation;
import java.util.List;

public record ChatAnswer(Long sessionId, String answer, String mode, List<Citation> citations) {
    public ChatAnswer { citations = citations == null ? List.of() : List.copyOf(citations); }
}

