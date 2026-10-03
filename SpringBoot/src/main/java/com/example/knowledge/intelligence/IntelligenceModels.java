package com.example.knowledge.intelligence;

import java.time.LocalDateTime;
import java.util.List;

public final class IntelligenceModels {
    private IntelligenceModels() {}
    public record AnalysisRequest(String type, List<Long> documentIds, String focus) {}
    public record StudyRequest(List<Long> documentIds, Integer questionCount) {}
    public record SubmitRequest(List<Integer> answers) {}
    public record ResolveRequest(Boolean resolved) {}
    public record SourceCoverage(Long documentId, String fileName, int includedChunks, int totalChunks,
                                 int includedCharacters, int totalCharacters) {}
    public record Analysis(Long id, Long knowledgeBaseId, String type, String title, String content, String mode,
                           LocalDateTime createdAt, List<Long> documentIds, String focus, String sourceNotice,
                           List<SourceCoverage> sources) {}
    public record Card(String front, String back) {}
    public record Question(int id, String prompt, List<String> options) {}
    public record StoredQuestion(int id, String prompt, List<String> options, int correctIndex, String explanation) {
        public Question publicView() { return new Question(id, prompt, options); }
    }
    public record Study(Long id, Long knowledgeBaseId, String title, String mode, List<Card> cards,
                        List<Question> questions, LocalDateTime createdAt, String sourceNotice, List<SourceCoverage> sources) {}
    public record GradedQuestion(int questionId, String prompt, List<String> options, int selectedIndex,
                                 int correctIndex, String explanation, boolean correct) {}
    public record Grade(Long id, int score, int total, List<GradedQuestion> results, LocalDateTime createdAt) {}
    public record Mistake(Long id, Long studyId, String title, int questionId, String prompt, List<String> options,
                          int selectedIndex, int correctIndex, String explanation, boolean correct, boolean resolved,
                          LocalDateTime createdAt) {}
}
