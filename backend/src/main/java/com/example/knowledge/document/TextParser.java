package com.example.knowledge.document;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
public class TextParser implements DocumentParser {
    @Override public boolean supports(String fileType) { return "txt".equalsIgnoreCase(fileType); }

    @Override public ParsedDocument parse(InputStream inputStream) throws IOException {
        String text = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        return new ParsedDocument(text, List.of(new ParsedPage(1, text)));
    }
}

