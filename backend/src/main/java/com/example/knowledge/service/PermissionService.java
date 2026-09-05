package com.example.knowledge.service;

import com.example.knowledge.common.BusinessException;
import com.example.knowledge.common.ErrorCode;
import com.example.knowledge.entity.KnowledgeBase;
import com.example.knowledge.mapper.KnowledgeBaseMapper;
import com.example.knowledge.security.JwtPrincipal;
import org.springframework.stereotype.Service;

@Service
public class PermissionService {
    private final KnowledgeBaseMapper mapper;

    public PermissionService(KnowledgeBaseMapper mapper) { this.mapper = mapper; }

    public KnowledgeBase requireKnowledgeBaseAccess(Long id, JwtPrincipal user, boolean write) {
        KnowledgeBase base = mapper.selectById(id);
        if (base == null || base.getStatus() == null || base.getStatus() != 1) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        boolean admin = "ADMIN".equalsIgnoreCase(user.role());
        boolean owner = user.userId().equals(base.getOwnerId());
        if (!admin && (!owner || (write && !owner))) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return base;
    }
}

