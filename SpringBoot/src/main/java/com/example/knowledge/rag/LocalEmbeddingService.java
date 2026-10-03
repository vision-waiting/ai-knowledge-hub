package com.example.knowledge.rag;

import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
@ConditionalOnProperty(name = "knowledge.ai.embedding-enabled", havingValue = "false", matchIfMissing = true)
public class LocalEmbeddingService implements EmbeddingService {
    public static final int DIMENSIONS = 256;

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
        if (text == null || text.isBlank()) return List.of();
        String normalized = text.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        // 英文/数字按单词切分
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("[a-z0-9]+").matcher(normalized);
        while (matcher.find()) result.add(matcher.group());
        // 中文按单字 + 双字(bigram)切分，保证中文可被检索
        StringBuilder han = new StringBuilder();
        for (char ch : normalized.toCharArray()) {
            if (Character.UnicodeScript.of(ch) == Character.UnicodeScript.HAN) han.append(ch);
        }
        String cjk = han.toString();
        for (int i = 0; i < cjk.length(); i++) {
            result.add(String.valueOf(cjk.charAt(i)));
            if (i + 1 < cjk.length()) result.add(cjk.substring(i, i + 2));
        }
        return result;
    }
}
