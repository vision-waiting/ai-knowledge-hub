package com.example.knowledge.chat;

import com.example.knowledge.common.BusinessException;
import com.example.knowledge.common.ErrorCode;
import com.example.knowledge.common.Result;
import com.example.knowledge.security.CurrentUser;
import com.example.knowledge.service.PermissionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/** Only explicitly submitted negative feedback exposes a single question/answer to maintainers. */
@RestController @RequestMapping("/api/feedback/issues")
public class FeedbackController {
    private final JdbcTemplate jdbc;
    private final PermissionService permissions;
    public FeedbackController(JdbcTemplate jdbc, PermissionService permissions) { this.jdbc = jdbc; this.permissions = permissions; }
    public enum Status { OPEN, RESOLVED }
    public record Resolution(@NotNull Status status) {}
    public record Issue(long messageId, String question, String answer, String reason, String status) {}
    @GetMapping public Result<List<Issue>> list(@RequestParam Long knowledgeBaseId, @RequestParam(defaultValue="OPEN") Status status,
                                              @RequestParam(defaultValue="0") int offset) {
        if (offset < 0 || offset > 100000) throw new BusinessException(ErrorCode.BAD_REQUEST);
        permissions.requireKnowledgeBaseManagement(knowledgeBaseId, CurrentUser.require());
        return Result.success(jdbc.query("""
            SELECT m.id, m.content, m.feedback_reason, COALESCE(m.feedback_status,'OPEN') AS issue_status,
              (SELECT q.content FROM chat_message q WHERE q.session_id=m.session_id AND q.role='USER' AND q.id<m.id ORDER BY q.id DESC LIMIT 1) AS question
            FROM chat_message m JOIN chat_session s ON s.id=m.session_id
            WHERE s.knowledge_base_id=? AND m.role='ASSISTANT' AND m.feedback='NOT_HELPFUL'
              AND COALESCE(m.feedback_status,'OPEN')=? ORDER BY m.id DESC LIMIT 50 OFFSET ?
            """, (rs, row) -> new Issue(rs.getLong("id"), rs.getString("question"), rs.getString("content"),
                rs.getString("feedback_reason"), rs.getString("issue_status")), knowledgeBaseId, status.name(), offset));
    }
    @PutMapping("/{messageId}") public Result<Void> resolve(@PathVariable long messageId, @Valid @RequestBody Resolution body) {
        List<Long> baseIds = jdbc.query("SELECT s.knowledge_base_id FROM chat_message m JOIN chat_session s ON s.id=m.session_id WHERE m.id=? AND m.feedback='NOT_HELPFUL' AND m.role='ASSISTANT'",
                (rs, row) -> rs.getLong(1), messageId);
        if (baseIds.isEmpty()) throw new BusinessException(ErrorCode.NOT_FOUND);
        permissions.requireKnowledgeBaseManagement(baseIds.getFirst(), CurrentUser.require());
        jdbc.update("UPDATE chat_message SET feedback_status=? WHERE id=? AND feedback='NOT_HELPFUL'", body.status().name(), messageId);
        return Result.success(null);
    }
}
