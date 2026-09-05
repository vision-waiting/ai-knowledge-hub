package com.example.knowledge.document;

import com.example.knowledge.common.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class UploadValidationTest {
    @Test
    void executableFileIsRejected() {
        assertThrows(BusinessException.class, () -> StorageService.validate("malware.exe", 10));
    }
}

