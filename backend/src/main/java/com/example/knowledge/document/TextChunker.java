package com.example.knowledge.document;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Component
public class TextChunker {
    private final int chunkSize;
    private final int overlap;

    public TextChunker(@Value("${knowledge.chunk-size:800}") int chunkSize,
                       @Value("${knowledge.chunk-overlap:100}") int overlap) {
        if (chunkSize <= 0 || overlap < 0 || overlap >= chunkSize) throw new IllegalArgumentException("invalid chunk settings");
        this.chunkSize = chunkSize;
        this.overlap = overlap;
    }

    public List<TextChunk> chunk(String text, int pageNumber) {
        if (text == null || text.isBlank()) return List.of();
        List<String> tokens = tokenize(text);
        List<TextChunk> result = new ArrayList<>();
        int step = chunkSize - overlap;
        for (int start = 0; start < tokens.size(); start += step) {
            int end = Math.min(start + chunkSize, tokens.size());
            String content = String.join(" ", tokens.subList(start, end)).trim();
            if (!content.isBlank()) result.add(new TextChunk(content, pageNumber, end - start));
            if (end == tokens.size()) break;
        }
        return result;
    }

    private List<String> tokenize(String text) {
        String normalized = text.replaceAll("\\s+", " ").trim();
        if (normalized.contains(" ")) return Arrays.asList(normalized.split(" "));
        List<String> characters = new ArrayList<>();
        normalized.codePoints().forEach(cp -> characters.add(new String(Character.toChars(cp))));
        return characters;
    }
}

