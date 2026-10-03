package com.example.knowledge.document;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import static org.junit.jupiter.api.Assertions.*;

class OfficeParserTest {
    @Test void wordTablesAreIncludedInReadingOrder() throws Exception {
        try (var document = new XWPFDocument(); var output = new ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText("Opening paragraph");
            document.createTable(1, 1).getRow(0).getCell(0).setText("Important table evidence");
            document.createParagraph().createRun().setText("Closing paragraph");
            document.write(output);
            var parsed = new WordParser().parse(new ByteArrayInputStream(output.toByteArray()));
            assertTrue(parsed.text().contains("Important table evidence"));
            assertTrue(parsed.text().indexOf("Opening") < parsed.text().indexOf("Important"));
            assertTrue(parsed.text().indexOf("Important") < parsed.text().indexOf("Closing"));
        }
    }

    @Test void slideTablesAreIncludedAndKeepTheirSlideNumber() throws Exception {
        try (var document = new XMLSlideShow(); var output = new ByteArrayOutputStream()) {
            document.createSlide().createTextBox().setText("First slide");
            document.createSlide().createTable(1, 1).getCell(0, 0).setText("Second slide table");
            document.write(output);
            var parsed = new PptParser().parse(new ByteArrayInputStream(output.toByteArray()));
            assertEquals(2, parsed.pages().size());
            assertEquals(2, parsed.pages().get(1).pageNumber());
            assertTrue(parsed.pages().get(1).text().contains("Second slide table"));
        }
    }
}
