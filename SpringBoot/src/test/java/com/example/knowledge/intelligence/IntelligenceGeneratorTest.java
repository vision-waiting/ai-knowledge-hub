package com.example.knowledge.intelligence;

import com.example.knowledge.common.BusinessException;
import com.example.knowledge.common.ErrorCode;
import com.example.knowledge.entity.DocumentChunk;
import com.example.knowledge.rag.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class IntelligenceGeneratorTest {
    private final AiService ai = mock(AiService.class);
    private final ObjectMapper json = new ObjectMapper();
    private final IntelligenceGenerator generator = new IntelligenceGenerator(ai, json);
    private List<RetrievedChunk> context() {
        return context("缓存可以减少数据库请求。缓存过期策略包括定时过期。");
    }
    private List<RetrievedChunk> context(String text) {
        DocumentChunk chunk = new DocumentChunk();
        chunk.setDocumentId(7L); chunk.setContent(text);
        return List.of(new RetrievedChunk(chunk, 1, 1, 1));
    }
    @Test void localStudyUsesEveryDistinctBlankOffsetAndRejectsTooManyQuestions() {
        when(ai.mode()).thenReturn("LOCAL_DEMO");
        var learning = generator.study(3, context("甲乙丙丁戊己"));
        assertThat(learning.questions()).extracting(q -> q.options().get(q.correctIndex()))
                .containsExactly("甲乙丙丁", "乙丙丁戊", "丙丁戊己");
        assertThat(learning.questions()).extracting(q -> q.prompt().replaceFirst("原文填空 \\d+：", ""))
                .doesNotHaveDuplicates();
        // Repeated source paragraphs must not inflate the number of distinct exercises.
        assertThatThrownBy(() -> generator.study(4, context("甲乙丙丁戊己\n甲乙丙丁戊己")))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.errorCode()).isEqualTo(ErrorCode.INSUFFICIENT_CONTEXT));
        verify(ai, never()).generate(any());
    }
    private String valid() throws Exception {
        var cards = List.of(Map.of("front", "作用", "back", "减少请求 [1]", "sourceIndex", 1, "evidence", "缓存可以减少数据库请求"),
                Map.of("front", "策略", "back", "定时过期 [1]", "sourceIndex", 1, "evidence", "缓存过期策略包括定时过期"),
                Map.of("front", "回顾", "back", "缓存 [1]", "sourceIndex", 1, "evidence", "缓存可以减少数据库请求"));
        var questions = new ArrayList<Map<String, Object>>();
        for (int i = 0; i < 3; i++) questions.add(Map.of("prompt", "问题" + i, "options", List.of("减少请求", "增加请求"),
                "correctIndex", 0, "explanation", "缓存减少请求 [1]", "sourceIndex", 1, "evidence", "缓存可以减少数据库请求"));
        return json.writeValueAsString(Map.of("cards", cards, "questions", questions));
    }
    private void response(String value) {
        when(ai.mode()).thenReturn("OPENAI_COMPATIBLE");
        when(ai.generate(any())).thenReturn(new AiResponse(value, "OPENAI_COMPATIBLE"));
    }
    @Test void acceptsGroundedStudyAndRejectsMalformedOrUnsupportedOutput() throws Exception {
        String valid = valid(); response(valid);
        assertThat(generator.study(3, context()).questions()).hasSize(3);
        for (String invalid : List.of(valid + " {}", valid.replace("缓存可以减少数据库请求", "完全不存在的原文内容"),
                valid.replace("\"sourceIndex\":1", "\"sourceIndex\":9"), valid.replace("[1]", "[8]"),
                valid.replace("\"correctIndex\":0", "\"correctIndex\":0.5"), "{\"cards\":[],\"questions\":[]}")) {
            response(invalid);
            assertThatThrownBy(() -> generator.study(3, context())).isInstanceOf(BusinessException.class);
        }
    }
    @Test void comparisonIncludesDocumentBoundariesAndRejectsUnknownCitations() {
        response("缓存降低请求 [1]");
        var coverage = List.of(new IntelligenceModels.SourceCoverage(7L, "缓存说明.txt", 1, 1, 30, 30));
        generator.analyze("COMPARE", "", context(), coverage);
        verify(ai).generate(argThat(request -> request.question().contains("缓存说明.txt")
                && request.question().contains("文档 ID 7") && request.question().contains("资料编号 [1]")));
        response("编造 [2]");
        assertThatThrownBy(() -> generator.analyze("SUMMARY", "", context(), coverage)).isInstanceOf(BusinessException.class);
    }
}
