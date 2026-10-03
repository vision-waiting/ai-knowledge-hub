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
    @org.springframework.beans.factory.annotation.Autowired private TesseractOcr ocr;
    @Override public boolean supports(String fileType) { return "pdf".equalsIgnoreCase(fileType); }

    @Override public ParsedDocument parse(InputStream inputStream) throws IOException {
        try (PDDocument document = Loader.loadPDF(inputStream.readAllBytes())) {
            PDFTextStripper stripper = new PDFTextStripper();
            List<ParsedPage> pages = new ArrayList<>();
            StringBuilder full = new StringBuilder();
            int ocrPages = 0;
            for (int page = 1; page <= document.getNumberOfPages(); page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String text = stripper.getText(document).trim();
                if (text.isBlank() && ocr != null && ocr.enabled()) {
                    if (++ocrPages > 100) throw new IOException("单份文档最多 OCR 100 页，请拆分后上传");
                    var bounds = document.getPage(page - 1).getCropBox();
                    float scale = 150f / 72f;
                    if (bounds.getWidth()*bounds.getHeight()*scale*scale > 20_000_000) throw new IOException("扫描页尺寸过大，请缩小后上传");
                    var bitmap = new org.apache.pdfbox.rendering.PDFRenderer(document).renderImageWithDPI(page - 1,150);
                    try { text = ocr.recognize(bitmap); } finally { bitmap.flush(); }
                }
                pages.add(new ParsedPage(page, text));
                if (!text.isBlank()) full.append(text).append('\n');
            }
            return new ParsedDocument(full.toString().trim(), pages);
        }
    }
}

