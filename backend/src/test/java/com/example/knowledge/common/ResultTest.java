package com.example.knowledge.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResultTest {
    @Test
    void successResultContainsCodeMessageDataAndTimestamp() {
        Result<String> result = Result.success("ok");
        assertEquals(200, result.code());
        assertEquals("success", result.message());
        assertEquals("ok", result.data());
        assertTrue(result.timestamp() > 0);
    }
}
