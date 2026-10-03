package com.example.knowledge.search;

public record SearchResult(Long documentId, String documentName, Long chunkId, Integer pageNumber,
                           Integer chunkIndex, String excerpt, double score) {}
