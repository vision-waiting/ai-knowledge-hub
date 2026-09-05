package com.example.knowledge.service;

import com.example.knowledge.mapper.KnowledgeBaseMapper;
import com.example.knowledge.vo.DashboardOverviewVO;
import org.springframework.stereotype.Service;

@Service
public class DashboardService {
    private final KnowledgeBaseMapper knowledgeBaseMapper;

    public DashboardService(KnowledgeBaseMapper knowledgeBaseMapper) { this.knowledgeBaseMapper = knowledgeBaseMapper; }

    public DashboardOverviewVO overview() {
        long count = knowledgeBaseMapper.selectCount(null);
        return new DashboardOverviewVO(count, 0, 0, 0, 0);
    }
}

