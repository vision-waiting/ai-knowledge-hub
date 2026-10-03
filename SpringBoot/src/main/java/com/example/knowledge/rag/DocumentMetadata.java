package com.example.knowledge.rag;

import java.util.List;

public record DocumentMetadata(String summary, List<String> tags, String mode) {
    public DocumentMetadata { tags = tags == null ? List.of() : List.copyOf(tags); }
}
