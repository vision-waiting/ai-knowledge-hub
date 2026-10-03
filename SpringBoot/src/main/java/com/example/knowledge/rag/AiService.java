package com.example.knowledge.rag;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public interface AiService {
    String INSUFFICIENT_CONTEXT = "当前知识库中没有找到足够相关的资料，因此无法基于当前知识库准确回答该问题。";

    AiResponse generate(AiRequest request);

    default String mode() { return "LOCAL_DEMO"; }

    default DocumentMetadata describeDocument(String text) {
        String normalized = text == null ? "" : text.replaceAll("\\s+", " ").trim();
        String summary = normalized.substring(0, Math.min(normalized.length(), 240));
        java.util.List<String> tags = LocalEmbeddingService.tokens(normalized).stream()
                .filter(token -> token.length() >= 2).distinct().limit(5).toList();
        return new DocumentMetadata(summary, tags, mode());
    }

    default AiResponse stream(AiRequest request, Consumer<String> onFragment, BooleanSupplier cancelled) {
        AiResponse response = generate(request);
        if (!cancelled.getAsBoolean()) onFragment.accept(response.answer());
        return response;
    }
}
