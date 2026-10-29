package com.example.knowledge.document;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MarkdownParserTest {
    @Test
    void preservesStructuralMarkersAndCodeExactly() throws Exception {
        String source = "# 父标题\n## 子标题\n\n|字段|说明|\n|---|---|\n|id|编号|\n\n```java\n    String value = `literal`;\n```";
        var parsed = new MarkdownParser().parse(new ByteArrayInputStream(source.getBytes(StandardCharsets.UTF_8)));
        org.junit.jupiter.api.Assertions.assertEquals(source, parsed.text());
    }
    @Test
    void markdownParserKeepsHeadingAndParagraphText() throws Exception {
        ParsedDocument parsed = new MarkdownParser().parse(new ByteArrayInputStream("# Java\n\nHashMap stores key values.".getBytes(StandardCharsets.UTF_8)));
        assertTrue(parsed.text().contains("Java"));
        assertTrue(parsed.text().contains("HashMap"));
    }
}

