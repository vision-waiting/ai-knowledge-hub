package com.example.knowledge.document;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MarkdownParserTest {
    @Test
    void markdownParserKeepsHeadingAndParagraphText() throws Exception {
        ParsedDocument parsed = new MarkdownParser().parse(new ByteArrayInputStream("# Java\n\nHashMap stores key values.".getBytes(StandardCharsets.UTF_8)));
        assertTrue(parsed.text().contains("Java"));
        assertTrue(parsed.text().contains("HashMap"));
    }
}

