package com.example.knowledge.document;
import org.junit.jupiter.api.Test;
import org.apache.pdfbox.pdmodel.*;
import java.io.*;
import static org.junit.jupiter.api.Assertions.*;
class PdfOcrTest {
 @Test void scanPagesUseConfiguredOcrAndRetainPageNumber() throws Exception {
  var parser=new PdfParser();
  var ocr=org.mockito.Mockito.mock(TesseractOcr.class);
  org.mockito.Mockito.when(ocr.enabled()).thenReturn(true);
  org.mockito.Mockito.when(ocr.recognize(org.mockito.ArgumentMatchers.any())).thenReturn("识别得到的扫描页内容");
  org.springframework.test.util.ReflectionTestUtils.setField(parser,"ocr",ocr);
  var bytes=new ByteArrayOutputStream();try(var pdf=new PDDocument()){pdf.addPage(new PDPage());pdf.save(bytes);}
  var parsed=parser.parse(new ByteArrayInputStream(bytes.toByteArray()));
  assertEquals("识别得到的扫描页内容",parsed.text());assertEquals(1,parsed.pages().getFirst().pageNumber());
 }
}
