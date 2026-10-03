package com.example.knowledge.notes;

import com.example.knowledge.common.BusinessException;
import com.example.knowledge.common.ErrorCode;
import com.example.knowledge.security.JwtPrincipal;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
public class NoteService {
    private final JdbcTemplate jdbc;
    private static final RowMapper<NoteVO> ROW = (rs, row) -> new NoteVO(rs.getLong("id"), rs.getString("title"),
            rs.getString("content"), rs.getString("category"), rs.getObject("source_session_id", Long.class),
            rs.getTimestamp("created_at").toLocalDateTime(), rs.getTimestamp("updated_at").toLocalDateTime());

    public NoteService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<NoteVO> list(JwtPrincipal user, String keyword, String category) {
        StringBuilder sql = new StringBuilder("SELECT * FROM personal_note WHERE user_id = ?");
        List<Object> args = new ArrayList<>(); args.add(user.userId());
        if (keyword != null && !keyword.isBlank()) {
            if (keyword.length() > 200) throw new BusinessException(ErrorCode.BAD_REQUEST, "搜索词不能超过 200 字");
            // Treat SQL wildcard characters as literal user input.
            String escaped = keyword.trim().replace("!", "!!").replace("%", "!%").replace("_", "!_");
            sql.append(" AND (LOWER(title) LIKE LOWER(?) ESCAPE '!' OR LOWER(content) LIKE LOWER(?) ESCAPE '!')");
            args.add("%" + escaped + "%"); args.add("%" + escaped + "%");
        }
        if (category != null && !category.isBlank()) {
            if (category.length() > 80) throw new BusinessException(ErrorCode.BAD_REQUEST);
            sql.append(" AND category = ?"); args.add(category.trim());
        }
        sql.append(" ORDER BY updated_at DESC, id DESC");
        return jdbc.query(sql.toString(), ROW, args.toArray());
    }

    public List<String> categories(JwtPrincipal user) {
        return jdbc.queryForList("SELECT DISTINCT category FROM personal_note WHERE user_id = ? ORDER BY category", String.class, user.userId());
    }

    public NoteVO get(Long id, JwtPrincipal user) {
        return jdbc.query("SELECT * FROM personal_note WHERE id = ? AND user_id = ?", ROW, id, user.userId())
                .stream().findFirst().orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    @Transactional
    public NoteVO create(NoteRequest request, JwtPrincipal user) {
        validate(request);
        validateSource(request.sourceSessionId(), user);
        GeneratedKeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement("INSERT INTO personal_note (user_id,title,content,category,source_session_id) VALUES (?,?,?,?,?)", new String[]{"id"});
            statement.setLong(1, user.userId()); statement.setString(2, request.title().trim());
            statement.setString(3, request.content()); statement.setString(4, category(request));
            statement.setObject(5, request.sourceSessionId()); return statement;
        }, key);
        return get(Objects.requireNonNull(key.getKey()).longValue(), user);
    }

    @Transactional
    public NoteVO update(Long id, NoteRequest request, JwtPrincipal user) {
        NoteVO existing = get(id, user);
        validate(request);
        // Saved notes survive source conversation deletion; only a newly attached source needs revalidation.
        if (!Objects.equals(existing.sourceSessionId(), request.sourceSessionId())) validateSource(request.sourceSessionId(), user);
        int changed = jdbc.update("UPDATE personal_note SET title=?,content=?,category=?,source_session_id=?,updated_at=CURRENT_TIMESTAMP WHERE id=? AND user_id=?",
                request.title().trim(), request.content(), category(request), request.sourceSessionId(), id, user.userId());
        if (changed == 0) throw new BusinessException(ErrorCode.NOT_FOUND);
        return get(id, user);
    }

    @Transactional
    public void delete(Long id, JwtPrincipal user) {
        if (jdbc.update("DELETE FROM personal_note WHERE id = ? AND user_id = ?", id, user.userId()) == 0)
            throw new BusinessException(ErrorCode.NOT_FOUND);
    }

    private void validateSource(Long sourceId, JwtPrincipal user) {
        if (sourceId != null && (sourceId <= 0 || jdbc.queryForObject("SELECT COUNT(*) FROM chat_session WHERE id = ? AND user_id = ?", Long.class, sourceId, user.userId()) == 0))
            throw new BusinessException(ErrorCode.NOT_FOUND, "来源会话不存在或不属于当前用户");
    }

    private void validate(NoteRequest value) {
        if (value.title() == null || value.title().isBlank() || value.title().length() > 200
                || value.content() == null || value.content().isBlank() || value.content().length() > 200000
                || (value.category() != null && value.category().length() > 80))
            throw new BusinessException(ErrorCode.BAD_REQUEST, "标题和内容不能为空，标题最多 200 字、内容最多 200000 字、分类最多 80 字");
    }

    private String category(NoteRequest request) { return request.category() == null || request.category().isBlank() ? "未分类" : request.category().trim(); }
}
