package com.example.knowledge.document;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
public class MarkdownParser implements DocumentParser {
    @Override public boolean supports(String fileType) { return "md".equalsIgnoreCase(fileType) || "markdown".equalsIgnoreCase(fileType); }

    @Override public ParsedDocument parse(InputStream inputStream) throws IOException {
        String source = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        // Keep heading hierarchy, table separators and fenced code as retrieval evidence.
        String text = source.strip();
        return new ParsedDocument(text, List.of(new ParsedPage(1, text)));
    }
}

