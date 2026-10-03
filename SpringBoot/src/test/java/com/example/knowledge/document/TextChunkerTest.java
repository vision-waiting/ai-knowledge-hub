package com.example.knowledge.document;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class TextChunkerTest {
    @Test
    void paragraphBoundaryWinsOverLaterSentenceBoundary() {
        var chunks = new TextChunker(12, 2).chunk("一二三四五六\n\n七八。九十甲乙丙丁", 4);
        assertEquals("一二三四五六", chunks.get(0).content());
        org.junit.jupiter.api.Assertions.assertTrue(chunks.get(chunks.size() - 1).content().endsWith("丙丁"));
        org.junit.jupiter.api.Assertions.assertTrue(chunks.stream().allMatch(chunk -> chunk.tokenCount() <= 12));
    }

    @Test
    void maximumOverlapStillMakesProgressAndRetainsTail() {
        var chunks = new TextChunker(4, 3).chunk("一二。三四五六七八九", 1);
        assertEquals(7, chunks.size());
        assertEquals("六七八九", chunks.get(6).content());
    }

    @Test
    void prefersSentenceBoundaryAndKeepsOverlap() {
        var chunks = new TextChunker(10, 2).chunk("知识管理很重要。检索增强生成用于问答。", 2);
        assertEquals("知识管理很重要。", chunks.get(0).content());
        org.junit.jupiter.api.Assertions.assertTrue(chunks.get(1).content().startsWith("要。"));
        org.junit.jupiter.api.Assertions.assertTrue(chunks.stream().allMatch(chunk -> chunk.tokenCount() <= 10));
    }

    @Test
    void preservesParagraphsAndCodeIndentation() {
        String source = "# 标题\n\n说明文字\n\n```java\n    return value;\n```";
        assertEquals(source, new TextChunker(100, 10).chunk(source, 1).get(0).content());
    }

    @Test
    void chineseChunksPreserveOriginalTextWithoutInsertedSpaces() {
        var chunks = new TextChunker(4, 1).chunk("知识管理系统测试", 3);
        assertEquals("知识管理", chunks.get(0).content());
        assertEquals("理系统测", chunks.get(1).content());
        assertEquals(3, chunks.get(0).pageNumber());
    }

    @Test
    void mixedChineseAndEnglishTextDoesNotCreateOversizedChunks() {
        var chunks = new TextChunker(8, 1).chunk("知识管理系统".repeat(8) + " Java overview", 1);
        org.junit.jupiter.api.Assertions.assertTrue(chunks.size() > 4);
        org.junit.jupiter.api.Assertions.assertTrue(chunks.stream().allMatch(chunk -> chunk.tokenCount() <= 8));
    }

    @Test
    void chunkerCreatesOverlappingNonEmptyChunks() {
        String text = "one two three four five six seven eight nine ten";
        var chunks = new TextChunker(5, 2).chunk(text, 1);
        assertEquals(3, chunks.size());
        assertFalse(chunks.get(0).content().isBlank());
        org.junit.jupiter.api.Assertions.assertTrue(chunks.get(1).content().startsWith("four five"));
    }
}
