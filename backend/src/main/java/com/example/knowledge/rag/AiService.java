package com.example.knowledge.rag;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public interface AiService {
    AiResponse generate(AiRequest request);

    default AiResponse stream(AiRequest request, Consumer<String> onFragment, BooleanSupplier cancelled) {
        AiResponse response = generate(request);
        if (!cancelled.getAsBoolean()) onFragment.accept(response.answer());
        return response;
    }
}

