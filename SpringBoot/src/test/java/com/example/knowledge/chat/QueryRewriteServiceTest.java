package com.example.knowledge.chat;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class QueryRewriteServiceTest {
    @Test
    void followUpPronounIsExpandedWithPreviousQuestion() {
        QueryRewriteService service = new QueryRewriteService();
        assertEquals("Spring Boot 是什么？ 它和 Spring 有什么区别？", service.rewrite("它和 Spring 有什么区别？", List.of("Spring Boot 是什么？")));
    }

    @Test
    void followUpUsesNewestHistoryQuestion() {
        QueryRewriteService service = new QueryRewriteService();
        assertEquals("上一轮问题？ 它和 Spring 有什么区别？",
                service.rewrite("它和 Spring 有什么区别？", List.of("上一轮问题？", "最旧问题？")));
    }

    @Test
    void chainedFollowUpsRetainOriginalSubject() {
        QueryRewriteService service = new QueryRewriteService();
        assertEquals("Redis persistence 它有什么限制？ 为什么？",
                service.rewrite("为什么？", List.of("它有什么限制？", "Redis persistence", "old subject")));
    }

    @Test
    void unrelatedNewQuestionDoesNotInheritPreviousTopic() {
        assertEquals("How does PostgreSQL work?", new QueryRewriteService().rewrite("How does PostgreSQL work?", List.of("Redis persistence")));
    }
}

