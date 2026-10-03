package com.example.knowledge.document;

import java.util.List;

public record ParsedDocument(String text, List<ParsedPage> pages) {
    public ParsedDocument {
        text = text == null ? "" : text;
        pages = pages == null ? List.of() : List.copyOf(pages);
    }
}

