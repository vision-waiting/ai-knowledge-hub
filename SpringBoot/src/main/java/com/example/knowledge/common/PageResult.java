package com.example.knowledge.common;

import java.util.List;

public record PageResult<T>(List<T> records, long total, long current, long size) {
    public PageResult {
        records = records == null ? List.of() : List.copyOf(records);
    }
}

