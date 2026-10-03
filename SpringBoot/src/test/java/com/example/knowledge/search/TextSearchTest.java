package com.example.knowledge.search;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TextSearchTest {
    @Test void supportsSingleChineseCharactersAndCountsDistinctTerms() {
        assertEquals(1.0, TextSearch.score("水资源与缓存", "水"));
        assertEquals(1.0, TextSearch.score("Redis", "Redis redis"));
        assertEquals(0.5, TextSearch.score("Redis", "Redis redis 缓存"));
    }
    @Test void scoresMatchingTermsAndIgnoresEmptyQueries() {
        assertEquals(1.0, TextSearch.score("Redis 缓存穿透与缓存击穿", "Redis 缓存"));
        assertEquals(0, TextSearch.score("正文", ""));
    }
}
