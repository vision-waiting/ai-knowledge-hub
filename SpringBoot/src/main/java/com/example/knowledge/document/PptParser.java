package com.example.knowledge.document;

import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.apache.poi.xslf.usermodel.XSLFTable;
import org.apache.poi.xslf.usermodel.XSLFGroupShape;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class PptParser implements DocumentParser {
    @Override public boolean supports(String fileType) { return "pptx".equalsIgnoreCase(fileType); }

    @Override public ParsedDocument parse(InputStream inputStream) throws IOException {
        try (XMLSlideShow show = new XMLSlideShow(inputStream)) {
            List<ParsedPage> pages = new ArrayList<>();
            for (int index = 0; index < show.getSlides().size(); index++) {
                String text = show.getSlides().get(index).getShapes().stream()
                        .map(this::shapeText)
                        .filter(value -> value != null && !value.isBlank()).collect(Collectors.joining("\n"));
                pages.add(new ParsedPage(index + 1, text));
            }
            String full = pages.stream().map(ParsedPage::text).filter(value -> !value.isBlank()).collect(Collectors.joining("\n"));
            return new ParsedDocument(full, pages);
        }
    }

    private String shapeText(XSLFShape shape) {
        if (shape instanceof XSLFTextShape text) return text.getText();
        if (shape instanceof XSLFTable table) return table.getRows().stream()
                .map(row -> row.getCells().stream().map(XSLFTextShape::getText).collect(Collectors.joining("\t")))
                .collect(Collectors.joining("\n"));
        if (shape instanceof XSLFGroupShape group) return group.getShapes().stream().map(this::shapeText).collect(Collectors.joining("\n"));
        return "";
    }
}

