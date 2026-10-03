package com.example.knowledge.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.knowledge.common.BusinessException;
import com.example.knowledge.common.ErrorCode;
import com.example.knowledge.dto.KnowledgeBaseRequest;
import com.example.knowledge.entity.KnowledgeBase;
import com.example.knowledge.entity.ChatSession;
import com.example.knowledge.entity.ChatMessage;
import com.example.knowledge.mapper.KnowledgeBaseMapper;
import com.example.knowledge.mapper.ChatSessionMapper;
import com.example.knowledge.mapper.ChatMessageMapper;
import com.example.knowledge.mapper.SysUserMapper;
import com.example.knowledge.security.JwtPrincipal;
import com.example.knowledge.vo.KnowledgeBaseVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class KnowledgeBaseService {
    private final KnowledgeBaseMapper mapper;
    private final DocumentService documentService;
    private final ChatSessionMapper sessionMapper;
    private final ChatMessageMapper messageMapper;
    private final PermissionService permissions;
    private final SysUserMapper users;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;

    public KnowledgeBaseService(KnowledgeBaseMapper mapper, DocumentService documentService,
                                ChatSessionMapper sessionMapper, ChatMessageMapper messageMapper,
                                PermissionService permissions, SysUserMapper users, org.springframework.jdbc.core.JdbcTemplate jdbc) {
        this.mapper = mapper; this.documentService = documentService;
        this.sessionMapper = sessionMapper; this.messageMapper = messageMapper;
        this.permissions = permissions; this.users = users; this.jdbc = jdbc;
    }

    @Transactional
    public KnowledgeBaseVO create(KnowledgeBaseRequest request, JwtPrincipal user) {
        KnowledgeBase base = new KnowledgeBase();
        base.setName(request.name().trim());
        base.setDescription(request.description());
        base.setOwnerId(user.userId());
        base.setShared("ADMIN".equalsIgnoreCase(user.role()) || Boolean.TRUE.equals(request.shared()));
        base.setStatus(1);
        base.setDocumentCount(0);
        mapper.insert(base);
        return toVO(base, user);
    }

    public KnowledgeBaseVO get(Long id, JwtPrincipal user) {
        KnowledgeBase base = permissions.requireKnowledgeBaseAccess(id, user, false);
        return toVO(base, user);
    }

    public List<KnowledgeBaseVO> list(JwtPrincipal user, String keyword) {
        QueryWrapper<KnowledgeBase> query = permissions.visibleKnowledgeBases(user);
        if (keyword != null && !keyword.isBlank()) query.like("name", keyword.trim());
        return mapper.selectList(query).stream().map(base -> toVO(base, user)).toList();
    }

    @Transactional
    public KnowledgeBaseVO update(Long id, KnowledgeBaseRequest request, JwtPrincipal user) {
        KnowledgeBase base = permissions.requireKnowledgeBaseManagement(id, user);
        base.setName(request.name().trim());
        base.setDescription(request.description());
        var creator = users.selectById(base.getOwnerId());
        if (creator != null && "ADMIN".equalsIgnoreCase(creator.getRole())) base.setShared(true);
        else if (request.shared() != null) base.setShared(request.shared());
        mapper.updateById(base);
        return toVO(base, user);
    }

    @Transactional
    public void delete(Long id, JwtPrincipal user) {
        KnowledgeBase base = permissions.requireKnowledgeBaseManagement(id, user);
        if (mapper.selectOne(new QueryWrapper<KnowledgeBase>().eq("id", id).last("FOR UPDATE")) == null)
            throw new BusinessException(ErrorCode.NOT_FOUND);
        documentService.deleteKnowledgeBaseDocuments(id);
        List<Long> sessions = sessionMapper.selectList(new QueryWrapper<ChatSession>().eq("knowledge_base_id", id))
                .stream().map(ChatSession::getId).toList();
        if (!sessions.isEmpty()) messageMapper.delete(new QueryWrapper<ChatMessage>().in("session_id", sessions));
        sessionMapper.delete(new QueryWrapper<ChatSession>().eq("knowledge_base_id", id));
        for (String table : List.of("knowledge_member", "knowledge_content", "web_source", "review_card"))
            jdbc.update("DELETE FROM " + table + " WHERE knowledge_base_id=?", id);
        mapper.deleteById(id);
    }

    private KnowledgeBaseVO toVO(KnowledgeBase base, JwtPrincipal user) {
        var creator = base.getOwnerId() == null ? null : users.selectById(base.getOwnerId());
        return new KnowledgeBaseVO(base.getId(), base.getName(), base.getDescription(), base.getOwnerId(),
                base.getDocumentCount() == null ? 0 : base.getDocumentCount(), base.getCreatedAt(), base.getUpdatedAt(),
                creator == null ? null : creator.getUsername(), creator == null ? null : creator.getNickname(),
                Boolean.TRUE.equals(base.getShared()) || creator != null && "ADMIN".equalsIgnoreCase(creator.getRole()),
                creator != null && "ADMIN".equalsIgnoreCase(creator.getRole()), permissions.canWrite(base, user), permissions.canManage(base, user));
    }
}
