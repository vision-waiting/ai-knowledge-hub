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
        String text = source.replaceAll("(?m)^#{1,6}\\s*", "")
                .replaceAll("\\*\\*([^*]+)\\*\\*", "$1")
                .replaceAll("`([^`]+)`", "$1")
                .replaceAll("(?m)^[-*+]\\s+", "")
                .trim();
        return new ParsedDocument(text, List.of(new ParsedPage(1, text)));
    }
}

