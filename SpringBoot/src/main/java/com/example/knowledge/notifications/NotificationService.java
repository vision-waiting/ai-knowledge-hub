package com.example.knowledge.notifications;

import com.example.knowledge.common.BusinessException;
import com.example.knowledge.common.ErrorCode;
import com.example.knowledge.security.JwtPrincipal;
import com.example.knowledge.service.PermissionService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class NotificationService {
    private final JdbcTemplate jdbc;
    private final PermissionService permissions;
    private static final Map<String, String> LABELS = Map.of("UPLOADED", "已上传，等待处理", "REINDEXED", "已提交重新索引",
            "SUCCESS", "已完成处理", "FAILED", "处理失败，请查看文档详情", "DELETED", "已移入回收站", "RESTORED", "已从回收站恢复", "CONTENT_UPDATED", "知识内容已更新");
    // Check live user and knowledge-base state even for historical entries.
    private static final String VISIBLE = " FROM user_notification n JOIN knowledge_update_event e ON e.event_key=n.event_key "
            + "JOIN knowledge_base k ON k.id=e.knowledge_base_id JOIN sys_user u ON u.id=n.user_id "
            + "WHERE n.user_id=? AND u.status=1 AND k.status=1 AND (k.owner_id=u.id OR EXISTS (SELECT 1 FROM knowledge_member member WHERE member.knowledge_base_id=k.id AND member.user_id=u.id) OR k.shared=TRUE OR UPPER(u.role)='ADMIN') ";

    public NotificationService(JdbcTemplate jdbc, PermissionService permissions) { this.jdbc = jdbc; this.permissions = permissions; }

    public Subscription subscription(Long knowledgeBaseId, JwtPrincipal user) {
        permissions.requireKnowledgeBaseAccess(knowledgeBaseId, user, false);
        return new Subscription(jdbc.queryForObject("SELECT COUNT(*) FROM knowledge_subscription WHERE user_id=? AND knowledge_base_id=?",
                Long.class, user.userId(), knowledgeBaseId) > 0);
    }

    public Subscription follow(Long knowledgeBaseId, JwtPrincipal user) {
        permissions.requireKnowledgeBaseAccess(knowledgeBaseId, user, false);
        try {
            jdbc.update("INSERT INTO knowledge_subscription(user_id,knowledge_base_id,followed_at) VALUES(?,?,?)",
                    user.userId(), knowledgeBaseId, LocalDateTime.now());
        } catch (DuplicateKeyException alreadyFollowing) { /* Preserve original subscription time on retries. */ }
        return new Subscription(true);
    }

    public Subscription unfollow(Long knowledgeBaseId, JwtPrincipal user) {
        // Removing one's own subscription remains possible after access is revoked.
        jdbc.update("DELETE FROM knowledge_subscription WHERE user_id=? AND knowledge_base_id=?", user.userId(), knowledgeBaseId);
        return new Subscription(false);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(KnowledgeUpdate event) {
        if (!LABELS.containsKey(event.type())) throw new IllegalArgumentException("Unknown knowledge update type");
        try {
            jdbc.update("INSERT INTO knowledge_update_event(event_key,knowledge_base_id,document_id,document_name,event_type,occurred_at) VALUES(?,?,?,?,?,?)",
                    event.eventKey(), event.knowledgeBaseId(), event.documentId(), event.documentName(), event.type(), event.occurredAt());
        } catch (DuplicateKeyException alreadyRecorded) { return; }
        jdbc.update("INSERT INTO user_notification(user_id,event_key) SELECT s.user_id,? FROM knowledge_subscription s "
                + "JOIN knowledge_base k ON k.id=s.knowledge_base_id JOIN sys_user u ON u.id=s.user_id "
                + "WHERE s.knowledge_base_id=? AND s.followed_at<=? AND k.status=1 AND u.status=1 "
                + "AND (k.owner_id=u.id OR EXISTS (SELECT 1 FROM knowledge_member member WHERE member.knowledge_base_id=k.id AND member.user_id=u.id) OR k.shared=TRUE OR UPPER(u.role)='ADMIN')", event.eventKey(), event.knowledgeBaseId(), event.occurredAt());
    }

    public long unreadCount(JwtPrincipal user) {
        return jdbc.queryForObject("SELECT COUNT(*)" + VISIBLE + "AND n.read_at IS NULL", Long.class, user.userId());
    }

    public NotificationPage list(JwtPrincipal user, boolean unreadOnly, int page, int pageSize) {
        if (page < 1 || page > 100000 || pageSize < 1 || pageSize > 100) throw new BusinessException(ErrorCode.BAD_REQUEST, "分页参数无效");
        String filter = unreadOnly ? "AND n.read_at IS NULL " : "";
        long total = jdbc.queryForObject("SELECT COUNT(*)" + VISIBLE + filter, Long.class, user.userId());
        List<NotificationItem> items = jdbc.query("SELECT n.id, e.knowledge_base_id, k.name, e.document_id, e.document_name, e.event_type, n.read_at, e.occurred_at"
                        + VISIBLE + filter + "ORDER BY e.occurred_at DESC,n.id DESC LIMIT ? OFFSET ?",
                (rs, row) -> new NotificationItem(rs.getLong("id"), rs.getLong("knowledge_base_id"), rs.getString("name"),
                        rs.getObject("document_id", Long.class), rs.getString("document_name"), rs.getString("event_type"),
                        rs.getString("document_name") + "：" + LABELS.getOrDefault(rs.getString("event_type"), "已更新"),
                        rs.getTimestamp("read_at") != null, rs.getTimestamp("occurred_at").toLocalDateTime()), user.userId(), pageSize, (page - 1) * pageSize);
        return new NotificationPage(items, total, unreadCount(user));
    }

    public void markRead(Long id, JwtPrincipal user) {
        long visible = jdbc.queryForObject("SELECT COUNT(*)" + VISIBLE + "AND n.id=?", Long.class, user.userId(), id);
        if (visible == 0) throw new BusinessException(ErrorCode.NOT_FOUND);
        jdbc.update("UPDATE user_notification SET read_at=COALESCE(read_at,?) WHERE id=? AND user_id=?", LocalDateTime.now(), id, user.userId());
    }

    public void markAllRead(JwtPrincipal user) {
        // All entries are private to the caller; inaccessible entries cannot be returned by list/count.
        jdbc.update("UPDATE user_notification SET read_at=? WHERE user_id=? AND read_at IS NULL", LocalDateTime.now(), user.userId());
    }

    public record Subscription(boolean following) {}
    public record NotificationItem(long id, long knowledgeBaseId, String knowledgeBaseName, Long documentId,
                                   String documentName, String type, String message, boolean read, LocalDateTime createdAt) {}
    public record NotificationPage(List<NotificationItem> items, long total, long unreadCount) {}
}
