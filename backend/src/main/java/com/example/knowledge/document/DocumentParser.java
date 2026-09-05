package com.example.knowledge.document;

import java.io.IOException;
import java.io.InputStream;

public interface DocumentParser {
    boolean supports(String fileType);
    ParsedDocument parse(InputStream inputStream) throws IOException;
}

