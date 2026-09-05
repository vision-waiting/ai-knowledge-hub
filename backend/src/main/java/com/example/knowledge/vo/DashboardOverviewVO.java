package com.example.knowledge.vo;

public record DashboardOverviewVO(long knowledgeBaseCount, long documentCount, long chatCount,
                                  long aiCallCount, long failedDocumentCount) {
}

