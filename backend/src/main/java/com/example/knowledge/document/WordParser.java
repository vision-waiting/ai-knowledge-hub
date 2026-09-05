package com.example.knowledge.document;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

@Component
public class WordParser implements DocumentParser {
    @Override public boolean supports(String fileType) { return "docx".equalsIgnoreCase(fileType); }

    @Override public ParsedDocument parse(InputStream inputStream) throws IOException {
        try (XWPFDocument document = new XWPFDocument(inputStream)) {
            String text = document.getParagraphs().stream().map(p -> p.getText().trim()).filter(s -> !s.isBlank()).reduce((a, b) -> a + "\n" + b).orElse("");
            return new ParsedDocument(text, List.of(new ParsedPage(1, text)));
        }
    }
}

