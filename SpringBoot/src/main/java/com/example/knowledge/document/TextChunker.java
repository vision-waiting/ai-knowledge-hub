package com.example.knowledge.document;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class TextChunker {
    private static final Pattern TOKEN = Pattern.compile("[\\p{IsHan}\\p{IsHiragana}\\p{IsKatakana}\\p{IsHangul}]|[\\p{L}\\p{N}_]+|[^\\s]");
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
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n').strip();
        List<int[]> tokens = new ArrayList<>();
        Matcher matcher = TOKEN.matcher(normalized);
        while (matcher.find()) tokens.add(new int[]{matcher.start(), matcher.end()});
        List<TextChunk> result = new ArrayList<>();
        for (int start = 0; start < tokens.size();) {
            int end = Math.min(start + chunkSize, tokens.size());
            if (end < tokens.size()) end = boundary(normalized, tokens, start, end);
            String content = normalized.substring(tokens.get(start)[0], tokens.get(end - 1)[1]).trim();
            if (!content.isBlank()) result.add(new TextChunk(content, pageNumber, end - start));
            if (end == tokens.size()) break;
            start = Math.max(start + 1, end - overlap);
        }
        return result;
    }

    private int boundary(String text, List<int[]> tokens, int start, int end) {
        // Keep chunks substantial and guarantee progress even with a large overlap.
        int minimum = start + Math.max(overlap + 1, (chunkSize + 1) / 2);
        for (int i = end; i >= minimum; i--) {
            String gap = text.substring(tokens.get(i - 1)[1], tokens.get(i)[0]);
            if (gap.contains("\n\n")) return i;
        }
        for (int i = end; i >= minimum; i--) {
            String token = text.substring(tokens.get(i - 1)[0], tokens.get(i - 1)[1]);
            String gap = text.substring(tokens.get(i - 1)[1], tokens.get(i)[0]);
            if ("。！？!?".contains(token) || (".".equals(token) && !gap.isEmpty()) || gap.contains("\n")) return i;
        }
        return end;
    }

}
