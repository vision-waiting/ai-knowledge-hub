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
}

