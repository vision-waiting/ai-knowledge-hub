package com.example.knowledge.service;

import com.example.knowledge.mapper.KnowledgeBaseMapper;
import com.example.knowledge.mapper.DocumentMapper;
import com.example.knowledge.mapper.ChatSessionMapper;
import com.example.knowledge.mapper.AiUsageLogMapper;
import com.example.knowledge.entity.Document;
import com.example.knowledge.vo.DashboardOverviewVO;
import org.springframework.stereotype.Service;

@Service
public class DashboardService {
    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final DocumentMapper documentMapper;
    private final ChatSessionMapper chatSessionMapper;
    private final AiUsageLogMapper aiUsageLogMapper;
    @org.springframework.beans.factory.annotation.Autowired
    private StatisticsQueryService statistics;

    public DashboardService(KnowledgeBaseMapper knowledgeBaseMapper) { this(knowledgeBaseMapper, null, null, null); }

    @org.springframework.beans.factory.annotation.Autowired
    public DashboardService(KnowledgeBaseMapper knowledgeBaseMapper, DocumentMapper documentMapper, ChatSessionMapper chatSessionMapper, AiUsageLogMapper aiUsageLogMapper) { this.knowledgeBaseMapper = knowledgeBaseMapper; this.documentMapper = documentMapper; this.chatSessionMapper = chatSessionMapper; this.aiUsageLogMapper = aiUsageLogMapper; }

    public DashboardOverviewVO overview() {
        if (statistics != null) return statistics.overview(null);
        long count = knowledgeBaseMapper.selectCount(null);
        long documents = documentMapper == null ? 0 : documentMapper.selectCount(null);
        long chats = chatSessionMapper == null ? 0 : chatSessionMapper.selectCount(null);
        long aiCalls = aiUsageLogMapper == null ? 0 : aiUsageLogMapper.selectCount(null);
        long failed = documentMapper == null ? 0 : documentMapper.selectCount(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<Document>().eq("status", "FAILED"));
        return new DashboardOverviewVO(count, documents, chats, aiCalls, failed);
    }

    public DashboardOverviewVO overview(com.example.knowledge.security.JwtPrincipal user) {
        return statistics.overview(user);
    }
}
