package com.example.knowledge.rag;

public record Citation(int index, Long documentId, String documentName, Integer pageNumber, Long chunkId, String excerpt) {
}

