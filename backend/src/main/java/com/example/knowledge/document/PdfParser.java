package com.example.knowledge.document;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

@Component
public class PdfParser implements DocumentParser {
    @Override public boolean supports(String fileType) { return "pdf".equalsIgnoreCase(fileType); }

    @Override public ParsedDocument parse(InputStream inputStream) throws IOException {
        try (PDDocument document = Loader.loadPDF(inputStream.readAllBytes())) {
            PDFTextStripper stripper = new PDFTextStripper();
            List<ParsedPage> pages = new ArrayList<>();
            StringBuilder full = new StringBuilder();
            for (int page = 1; page <= document.getNumberOfPages(); page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String text = stripper.getText(document).trim();
                pages.add(new ParsedPage(page, text));
                if (!text.isBlank()) full.append(text).append('\n');
            }
            return new ParsedDocument(full.toString().trim(), pages);
        }
    }
}

