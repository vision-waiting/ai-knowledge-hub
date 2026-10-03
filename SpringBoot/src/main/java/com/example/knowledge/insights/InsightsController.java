package com.example.knowledge.insights;

import com.example.knowledge.common.BusinessException;
import com.example.knowledge.common.ErrorCode;
import com.example.knowledge.common.Result;
import com.example.knowledge.security.CurrentUser;
import com.example.knowledge.service.PermissionService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Answer-quality signals for maintainers. Unanswered questions come from private sessions, so they are only
 * surfaced as anonymous groups that several different users asked; everything else is reported as counts.
 */
@RestController @RequestMapping("/api/knowledge-bases/{kb}/insights")
public class InsightsController {
    static final int MIN_DISTINCT_USERS = 2;
    private final JdbcTemplate jdbc; private final PermissionService permissions;
    public InsightsController(JdbcTemplate jdbc, PermissionService permissions) { this.jdbc = jdbc; this.permissions = permissions; }

    public record Gap(String question, int askCount, int userCount, LocalDateTime lastAskedAt) {}
    public record Insights(int days, long answeredCount, long insufficientCount, long helpfulCount, long notHelpfulCount,
                           long openIssueCount, long resolvedIssueCount, List<Gap> gaps, long hiddenGapCount) {}
    private record Unanswered(String question, long userId, LocalDateTime askedAt) {}

    @GetMapping public Result<Insights> insights(@PathVariable Long kb, @RequestParam(defaultValue = "30") int days) {
        if (days < 1 || days > 180) throw new BusinessException(ErrorCode.BAD_REQUEST, "统计天数必须在 1–180 之间");
        permissions.requireKnowledgeBaseManagement(kb, CurrentUser.require());
        Timestamp since = Timestamp.valueOf(LocalDateTime.now().minusDays(days));
        String answers = " FROM chat_message m JOIN chat_session s ON s.id=m.session_id WHERE s.knowledge_base_id=? AND m.role='ASSISTANT'";
        long answered = count("SELECT COUNT(*)" + answers + " AND COALESCE(m.mode,'')<>'SMALL_TALK' AND m.created_at>=?", kb, since);
        long insufficient = count("SELECT COUNT(*)" + answers + " AND m.mode='INSUFFICIENT_CONTEXT' AND m.created_at>=?", kb, since);
        long helpful = count("SELECT COUNT(*)" + answers + " AND m.feedback='HELPFUL' AND m.created_at>=?", kb, since);
        long notHelpful = count("SELECT COUNT(*)" + answers + " AND m.feedback='NOT_HELPFUL' AND m.created_at>=?", kb, since);
        long open = count("SELECT COUNT(*)" + answers + " AND m.feedback='NOT_HELPFUL' AND COALESCE(m.feedback_status,'OPEN')='OPEN'", kb);
        long resolved = count("SELECT COUNT(*)" + answers + " AND m.feedback='NOT_HELPFUL' AND m.feedback_status='RESOLVED'", kb);
        List<Unanswered> rows = jdbc.query("""
            SELECT s.user_id, m.created_at,
              (SELECT q.content FROM chat_message q WHERE q.session_id=m.session_id AND q.role='USER' AND q.id<m.id ORDER BY q.id DESC LIMIT 1) AS question
            """ + answers + " AND m.mode='INSUFFICIENT_CONTEXT' AND m.created_at>=? ORDER BY m.id DESC",
            (rs, i) -> new Unanswered(rs.getString("question"), rs.getLong("user_id"), rs.getTimestamp("created_at").toLocalDateTime()), kb, since);
        List<Gap> gaps = group(rows);
        long shown = gaps.stream().mapToLong(Gap::askCount).sum();
        return Result.success(new Insights(days, answered, insufficient, helpful, notHelpful, open, resolved, gaps, rows.size() - shown));
    }

    /** Rows arrive newest first, so the first original text seen for a group is its most recent wording. */
    static List<Gap> group(List<Unanswered> rows) {
        Map<String, List<Unanswered>> groups = new LinkedHashMap<>();
        for (Unanswered row : rows) {
            String key = normalize(row.question());
            if (!key.isEmpty()) groups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(row);
        }
        return groups.values().stream()
                .filter(items -> items.stream().map(Unanswered::userId).distinct().count() >= MIN_DISTINCT_USERS)
                .map(items -> new Gap(items.getFirst().question().trim(), items.size(),
                        (int) items.stream().map(Unanswered::userId).distinct().count(), items.getFirst().askedAt()))
                .sorted(Comparator.comparingInt(Gap::askCount).reversed().thenComparing(Gap::lastAskedAt, Comparator.reverseOrder()))
                .limit(20).toList();
    }
    static String normalize(String question) {
        if (question == null) return "";
        return question.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").replaceAll("[？?。.!！\\s]+$", "");
    }
    private long count(String sql, Object... args) { Long value = jdbc.queryForObject(sql, Long.class, args); return value == null ? 0 : value; }
}
