package com.example.knowledge.document;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class TextChunkerTest {
    @Test
    void chunkerCreatesOverlappingNonEmptyChunks() {
        String text = "one two three four five six seven eight nine ten";
        var chunks = new TextChunker(5, 2).chunk(text, 1);
        assertEquals(3, chunks.size());
        assertFalse(chunks.get(0).content().isBlank());
        org.junit.jupiter.api.Assertions.assertTrue(chunks.get(1).content().startsWith("four five"));
    }
}
