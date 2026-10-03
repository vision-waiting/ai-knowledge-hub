package com.example.knowledge.service;

import com.example.knowledge.security.JwtPrincipal;
import com.example.knowledge.vo.DashboardOverviewVO;
import com.example.knowledge.vo.DashboardOverviewVO.DailyCount;
import com.example.knowledge.vo.DashboardOverviewVO.NamedCount;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
public class StatisticsQueryService {
    private final JdbcTemplate jdbc;
    public StatisticsQueryService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public DashboardOverviewVO overview(JwtPrincipal user) {
        boolean global = user == null || "ADMIN".equals(user.role());
        Object[] args = global ? new Object[0] : new Object[]{user.userId()};
        String scope = global ? "" : " AND k.owner_id = ?";
        String personal = global ? "" : " AND user_id = ?";
        String documents = " FROM document d JOIN knowledge_base k ON k.id=d.knowledge_base_id WHERE k.status=1 AND d.status NOT IN ('ARCHIVED','TRASHED')" + scope;
        String questions = " FROM chat_message m JOIN chat_session s ON s.id=m.session_id WHERE m.role='USER'" + (global ? "" : " AND s.user_id = ?");
        long bases = count("SELECT COUNT(*) FROM knowledge_base k WHERE k.status=1" + scope, args);
        long docs = count("SELECT COUNT(*)" + documents, args);
        long chats = count("SELECT COUNT(*) FROM chat_session WHERE 1=1" + personal, args);
        long calls = count("SELECT COUNT(*) FROM ai_usage_log WHERE 1=1" + personal, args);
        long failed = count("SELECT COUNT(*)" + documents + " AND d.status='FAILED'", args);
        long processing = count("SELECT COUNT(*)" + documents + " AND d.status IN ('WAITING','PARSING','CHUNKING','EMBEDDING','INDEXING')", args);
        long chunks = count("SELECT COUNT(*) FROM document_chunk c JOIN document d ON d.id=c.document_id AND d.status='SUCCESS' JOIN knowledge_base k ON k.id=c.knowledge_base_id WHERE k.status=1" + scope, args);
        long today = timedCount("SELECT COUNT(*)" + questions, "m.created_at", args, LocalDate.now());
        List<DailyCount> weeklyQuestions = new ArrayList<>();
        List<DailyCount> weeklyUploads = new ArrayList<>();
        for (int offset = 6; offset >= 0; offset--) {
            LocalDate day = LocalDate.now().minusDays(offset);
            weeklyQuestions.add(new DailyCount(day.toString(), timedCount("SELECT COUNT(*)" + questions, "m.created_at", args, day)));
            weeklyUploads.add(new DailyCount(day.toString(), timedCount("SELECT COUNT(*)" + documents, "d.created_at", args, day)));
        }
        List<NamedCount> types = jdbc.query("SELECT d.file_type, COUNT(*)" + documents + " GROUP BY d.file_type ORDER BY COUNT(*) DESC", (rs, i) -> new NamedCount(rs.getString(1), rs.getLong(2)), args);
        List<NamedCount> ranking = jdbc.query("SELECT k.name, COUNT(d.id) FROM knowledge_base k LEFT JOIN document d ON d.knowledge_base_id=k.id AND d.status NOT IN ('ARCHIVED','TRASHED') WHERE k.status=1" + scope + " GROUP BY k.id,k.name ORDER BY COUNT(d.id) DESC LIMIT 8", (rs, i) -> new NamedCount(rs.getString(1), rs.getLong(2)), args);
        return new DashboardOverviewVO(bases, docs, chats, calls, failed, processing,
                global ? count("SELECT COUNT(*) FROM sys_user", new Object[0]) : 1, chunks, today, weeklyQuestions, weeklyUploads, types, ranking);
    }

    private long timedCount(String sql, String column, Object[] scope, LocalDate day) {
        List<Object> args = new ArrayList<>(List.of(scope));
        args.add(Timestamp.valueOf(day.atStartOfDay())); args.add(Timestamp.valueOf(day.plusDays(1).atStartOfDay()));
        return count(sql + " AND " + column + ">=? AND " + column + "<?", args.toArray());
    }
    private long count(String sql, Object[] args) { Long value = jdbc.queryForObject(sql, Long.class, args); return value == null ? 0 : value; }
}
