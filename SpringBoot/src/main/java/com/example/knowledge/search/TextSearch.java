package com.example.knowledge.search;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class TextSearch {
    private TextSearch() {}
    public static List<String> terms(String query) {
        if (query == null || query.isBlank()) return List.of();
        return Arrays.stream(query.toLowerCase(Locale.ROOT).trim().split("\\s+")).filter(term -> !term.isEmpty()).distinct().toList();
    }
    public static double score(String content, String query) {
        List<String> terms = terms(query);
        if (content == null || terms.isEmpty()) return 0;
        String haystack = content.toLowerCase(Locale.ROOT);
        return (double) terms.stream().filter(haystack::contains).count() / terms.size();
    }
}
