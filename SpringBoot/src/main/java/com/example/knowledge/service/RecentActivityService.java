package com.example.knowledge.service;

import com.example.knowledge.entity.KnowledgeBase;
import com.example.knowledge.mapper.KnowledgeBaseMapper;
import com.example.knowledge.security.JwtPrincipal;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/** Real "continue where you left off" data, limited to knowledge bases the user can still open. */
@Service
public class RecentActivityService {
    private final JdbcTemplate jdbc; private final KnowledgeBaseMapper bases; private final PermissionService permissions;
    public RecentActivityService(JdbcTemplate jdbc, KnowledgeBaseMapper bases, PermissionService permissions) {
        this.jdbc = jdbc; this.bases = bases; this.permissions = permissions;
    }

    public record RecentSession(long id, long knowledgeBaseId, String knowledgeBaseName, String title, LocalDateTime updatedAt) {}
    public record RecentDocument(long id, long knowledgeBaseId, String knowledgeBaseName, String fileName, String status, LocalDateTime createdAt) {}
    public record RecentActivity(List<RecentSession> sessions, List<RecentDocument> documents, List<RecentDocument> failedDocuments, long dueReviewCount) {}

    public RecentActivity recent(JwtPrincipal user) {
        Map<Long, String> names = bases.selectList(permissions.visibleKnowledgeBases(user)).stream()
                .collect(Collectors.toMap(KnowledgeBase::getId, KnowledgeBase::getName, (a, b) -> a));
        if (names.isEmpty()) return new RecentActivity(List.of(), List.of(), List.of(), 0);
        List<Object> ids = new ArrayList<>(names.keySet());
        String in = " IN (" + String.join(",", Collections.nCopies(ids.size(), "?")) + ")";
        List<RecentSession> sessions = jdbc.query("SELECT id,knowledge_base_id,title,updated_at FROM chat_session WHERE user_id=? AND knowledge_base_id"
                + in + " ORDER BY updated_at DESC, id DESC LIMIT 5", (rs, i) -> new RecentSession(rs.getLong("id"), rs.getLong("knowledge_base_id"),
                names.get(rs.getLong("knowledge_base_id")), rs.getString("title"), time(rs.getTimestamp("updated_at"))), with(user.userId(), ids));
        String documents = "SELECT id,knowledge_base_id,file_name,status,created_at FROM document WHERE deleted_at IS NULL"
                + " AND status NOT IN ('ARCHIVED','TRASHED') AND knowledge_base_id" + in;
        org.springframework.jdbc.core.RowMapper<RecentDocument> row = (rs, i) -> new RecentDocument(rs.getLong("id"), rs.getLong("knowledge_base_id"),
                names.get(rs.getLong("knowledge_base_id")), rs.getString("file_name"), rs.getString("status"), time(rs.getTimestamp("created_at")));
        List<RecentDocument> recent = jdbc.query(documents + " ORDER BY created_at DESC, id DESC LIMIT 5", row, ids.toArray());
        List<RecentDocument> failed = jdbc.query(documents + " AND status='FAILED' ORDER BY created_at DESC, id DESC LIMIT 5", row, ids.toArray());
        Long due = jdbc.queryForObject("SELECT COUNT(*) FROM review_card WHERE user_id=? AND due_at<=? AND knowledge_base_id" + in, Long.class,
                with(user.userId(), with(Timestamp.valueOf(LocalDateTime.now()), ids)));
        return new RecentActivity(sessions, recent, failed, due == null ? 0 : due);
    }

    private static Object[] with(Object first, List<Object> rest) { return with(first, rest.toArray()); }
    private static Object[] with(Object first, Object[] rest) {
        Object[] values = new Object[rest.length + 1]; values[0] = first; System.arraycopy(rest, 0, values, 1, rest.length); return values;
    }
    private static LocalDateTime time(Timestamp value) { return value == null ? null : value.toLocalDateTime(); }
}
