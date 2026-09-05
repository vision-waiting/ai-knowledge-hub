package com.example.knowledge.service;

import com.example.knowledge.mapper.KnowledgeBaseMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DashboardServiceTest {
    @Test
    void overviewReturnsKnowledgeBaseCount() {
        KnowledgeBaseMapper mapper = mock(KnowledgeBaseMapper.class);
        when(mapper.selectCount(org.mockito.ArgumentMatchers.any())).thenReturn(3L);
        var overview = new DashboardService(mapper).overview();
        assertEquals(3, overview.knowledgeBaseCount());
    }
}

