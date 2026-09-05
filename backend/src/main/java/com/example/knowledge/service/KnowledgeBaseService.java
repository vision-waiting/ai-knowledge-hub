package com.example.knowledge.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.knowledge.common.BusinessException;
import com.example.knowledge.common.ErrorCode;
import com.example.knowledge.dto.KnowledgeBaseRequest;
import com.example.knowledge.entity.KnowledgeBase;
import com.example.knowledge.mapper.KnowledgeBaseMapper;
import com.example.knowledge.security.JwtPrincipal;
import com.example.knowledge.vo.KnowledgeBaseVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class KnowledgeBaseService {
    private final KnowledgeBaseMapper mapper;

    public KnowledgeBaseService(KnowledgeBaseMapper mapper) { this.mapper = mapper; }

    @Transactional
    public KnowledgeBaseVO create(KnowledgeBaseRequest request, JwtPrincipal user) {
        KnowledgeBase base = new KnowledgeBase();
        base.setName(request.name().trim());
        base.setDescription(request.description());
        base.setOwnerId(user.userId());
        base.setStatus(1);
        base.setDocumentCount(0);
        mapper.insert(base);
        return toVO(base);
    }

    public KnowledgeBaseVO get(Long id, JwtPrincipal user) {
        KnowledgeBase base = find(id);
        checkAccess(base, user, false);
        return toVO(base);
    }

    public List<KnowledgeBaseVO> list(JwtPrincipal user, String keyword) {
        QueryWrapper<KnowledgeBase> query = new QueryWrapper<KnowledgeBase>().eq("status", 1);
        if (!"ADMIN".equalsIgnoreCase(user.role())) query.eq("owner_id", user.userId());
        if (keyword != null && !keyword.isBlank()) query.like("name", keyword.trim());
        return mapper.selectList(query).stream().map(this::toVO).toList();
    }

    @Transactional
    public KnowledgeBaseVO update(Long id, KnowledgeBaseRequest request, JwtPrincipal user) {
        KnowledgeBase base = find(id);
        checkAccess(base, user, true);
        base.setName(request.name().trim());
        base.setDescription(request.description());
        mapper.updateById(base);
        return toVO(base);
    }

    @Transactional
    public void delete(Long id, JwtPrincipal user) {
        KnowledgeBase base = find(id);
        checkAccess(base, user, true);
        mapper.deleteById(id);
    }

    private KnowledgeBase find(Long id) {
        KnowledgeBase base = mapper.selectById(id);
        if (base == null || base.getStatus() == null || base.getStatus() != 1) throw new BusinessException(ErrorCode.NOT_FOUND);
        return base;
    }

    private void checkAccess(KnowledgeBase base, JwtPrincipal user, boolean write) {
        if ("ADMIN".equalsIgnoreCase(user.role())) return;
        if (!user.userId().equals(base.getOwnerId())) throw new BusinessException(ErrorCode.FORBIDDEN);
    }

    private KnowledgeBaseVO toVO(KnowledgeBase base) {
        return new KnowledgeBaseVO(base.getId(), base.getName(), base.getDescription(), base.getOwnerId(),
                base.getDocumentCount() == null ? 0 : base.getDocumentCount(), base.getCreatedAt(), base.getUpdatedAt());
    }
}

