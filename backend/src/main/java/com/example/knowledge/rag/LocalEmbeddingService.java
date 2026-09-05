package com.example.knowledge.rag;

import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
@ConditionalOnProperty(name = "knowledge.ai.embedding-enabled", havingValue = "false", matchIfMissing = true)
public class LocalEmbeddingService implements EmbeddingService {
    public static final int DIMENSIONS = 64;

    @Override
    public List<Float> embed(String text) {
        float[] values = new float[DIMENSIONS];
        if (text != null) {
            for (String token : tokens(text)) {
                int index = Math.floorMod(token.hashCode(), DIMENSIONS);
                values[index] += 1f;
                values[(index * 31 + 7) % DIMENSIONS] += 0.5f;
            }
        }
        double norm = 0;
        for (float value : values) norm += value * value;
        norm = Math.sqrt(norm);
        List<Float> result = new ArrayList<>(DIMENSIONS);
        for (float value : values) result.add((float) (norm == 0 ? 0 : value / norm));
        return result;
    }

    @Override
    public List<List<Float>> embedBatch(List<String> texts) { return texts.stream().map(this::embed).toList(); }

    static List<String> tokens(String text) {
        String normalized = text == null ? "" : text.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
        if (normalized.isBlank()) return List.of();
        return List.of(normalized.split(" "));
    }
}
