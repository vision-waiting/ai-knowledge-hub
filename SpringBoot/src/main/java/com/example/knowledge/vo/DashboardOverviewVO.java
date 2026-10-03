package com.example.knowledge.vo;

public record DashboardOverviewVO(long knowledgeBaseCount, long documentCount, long chatCount,
                                  long aiCallCount, long failedDocumentCount, long processingCount,
                                  long userCount, long chunkCount, long todayQuestionCount,
                                  java.util.List<DailyCount> weeklyQuestions, java.util.List<DailyCount> weeklyUploads,
                                  java.util.List<NamedCount> documentTypes, java.util.List<NamedCount> knowledgeBaseRanking) {
    public DashboardOverviewVO(long bases, long docs, long chats, long calls, long failed) {
        this(bases, docs, chats, calls, failed, 0, 0, 0, 0, java.util.List.of(), java.util.List.of(), java.util.List.of(), java.util.List.of());
    }
    public record DailyCount(String date, long count) {}
    public record NamedCount(String name, long count) {}
}
