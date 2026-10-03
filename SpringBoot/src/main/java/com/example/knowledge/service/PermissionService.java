package com.example.knowledge.service;

import com.example.knowledge.common.BusinessException;
import com.example.knowledge.common.ErrorCode;
import com.example.knowledge.entity.KnowledgeBase;
import com.example.knowledge.mapper.KnowledgeBaseMapper;
import com.example.knowledge.mapper.SysUserMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.knowledge.security.JwtPrincipal;
import org.springframework.stereotype.Service;

@Service
public class PermissionService {
    private final KnowledgeBaseMapper mapper;

    private final SysUserMapper users;
    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbc;
    public PermissionService(KnowledgeBaseMapper mapper, SysUserMapper users) { this.mapper = mapper; this.users = users; }

    public QueryWrapper<KnowledgeBase> visibleKnowledgeBases(JwtPrincipal user) {
        QueryWrapper<KnowledgeBase> query = new QueryWrapper<KnowledgeBase>().eq("status", 1);
        if (!"ADMIN".equalsIgnoreCase(user.role())) {
            query.and(scope -> scope.eq("owner_id", user.userId()).or().eq("shared", true).or()
                    .inSql("owner_id", "SELECT id FROM sys_user WHERE UPPER(role) = 'ADMIN'")
                    .or().inSql("id", "SELECT knowledge_base_id FROM knowledge_member WHERE user_id = " + user.userId()));
        }
        return query;
    }

    public KnowledgeBase requireKnowledgeBaseAccess(Long id, JwtPrincipal user, boolean write) {
        KnowledgeBase base = mapper.selectById(id);
        if (base == null || base.getStatus() == null || base.getStatus() != 1) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        boolean admin = "ADMIN".equalsIgnoreCase(user.role());
        boolean owner = user.userId().equals(base.getOwnerId());
        var creator = !admin && !owner && !write ? users.selectById(base.getOwnerId()) : null;
        boolean shared = Boolean.TRUE.equals(base.getShared()) || creator != null && "ADMIN".equalsIgnoreCase(creator.getRole());
        String memberRole = admin || owner ? null : memberRole(id, user.userId());
        boolean memberAccess = write ? "EDITOR".equals(memberRole) || "MANAGER".equals(memberRole) : memberRole != null;
        if (!admin && !owner && !memberAccess && (write || !shared)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return base;
    }

    public KnowledgeBase requireKnowledgeBaseManagement(Long id, JwtPrincipal user) {
        KnowledgeBase base = requireKnowledgeBaseAccess(id, user, false);
        if (!"ADMIN".equalsIgnoreCase(user.role()) && !user.userId().equals(base.getOwnerId()) && !"MANAGER".equals(memberRole(id, user.userId())))
            throw new BusinessException(ErrorCode.FORBIDDEN);
        return base;
    }
    public boolean canManage(KnowledgeBase base, JwtPrincipal user) {
        return "ADMIN".equalsIgnoreCase(user.role()) || user.userId().equals(base.getOwnerId()) || "MANAGER".equals(memberRole(base.getId(), user.userId()));
    }
    public boolean canWrite(KnowledgeBase base, JwtPrincipal user) {
        return canManage(base, user) || "EDITOR".equals(memberRole(base.getId(), user.userId()));
    }
    private String memberRole(Long id, Long userId) {
        if (jdbc == null) return null;
        return jdbc.query("SELECT role FROM knowledge_member WHERE knowledge_base_id=? AND user_id=?", (rs, row) -> rs.getString(1), id, userId).stream().findFirst().orElse(null);
    }
}
