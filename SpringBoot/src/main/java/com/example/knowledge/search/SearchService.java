package com.example.knowledge.search;

import com.example.knowledge.common.BusinessException;
import com.example.knowledge.common.ErrorCode;
import com.example.knowledge.security.JwtPrincipal;
import com.example.knowledge.service.PermissionService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class SearchService {
    private final JdbcTemplate jdbc;
    private final PermissionService permissions;
    public SearchService(JdbcTemplate jdbc, PermissionService permissions) {
        this.jdbc = jdbc; this.permissions = permissions;
    }
    public List<SearchResult> search(Long knowledgeBaseId, String query, JwtPrincipal user) {
        return search(knowledgeBaseId, query, null, null, null, 0, 50, user);
    }
    public List<SearchResult> search(Long knowledgeBaseId, String query, String fileName, String fileType,
                                     String tag, int offset, int limit, JwtPrincipal user) {
        if (query == null || query.isBlank() || query.trim().length() > 200)
            throw new BusinessException(ErrorCode.BAD_REQUEST, "搜索词不能为空且不能超过 200 字");
        if (offset < 0 || limit < 1 || limit > 100)
            throw new BusinessException(ErrorCode.BAD_REQUEST, "分页参数无效，每页最多 100 条");
        validateFilter(fileName, 255); validateFilter(fileType, 50); validateFilter(tag, 100);
        permissions.requireKnowledgeBaseAccess(knowledgeBaseId, user, false);
        List<String> terms = TextSearch.terms(query);
        List<Object> args = new ArrayList<>();
        List<String> scores = new ArrayList<>();
        // LOCATE treats %, _ and backslashes literally. Only placeholder values come from users.
        for (String term : terms) { scores.add("CASE WHEN LOCATE(?, LOWER(c.content)) > 0 THEN 1 ELSE 0 END"); args.add(term); }
        StringBuilder sql = new StringBuilder("SELECT * FROM (SELECT d.id document_id, d.file_name, c.id chunk_id, c.page_number, c.chunk_index, c.content, (")
            .append(String.join(" + ", scores)).append(") hits FROM document d JOIN document_chunk c ON c.document_id=d.id AND c.knowledge_base_id=d.knowledge_base_id WHERE d.knowledge_base_id=? AND d.status='SUCCESS'");
        args.add(knowledgeBaseId);
        if (fileName != null && !fileName.isBlank()) { sql.append(" AND LOCATE(?, LOWER(d.file_name)) > 0"); args.add(fileName.trim().toLowerCase(Locale.ROOT)); }
        if (fileType != null && !fileType.isBlank()) { sql.append(" AND LOWER(d.file_type)=?"); args.add(fileType.trim().toLowerCase(Locale.ROOT)); }
        if (tag != null && !tag.isBlank()) { sql.append(" AND EXISTS (SELECT 1 FROM document_tag t WHERE t.document_id=d.id AND LOWER(t.tag_name)=?)"); args.add(tag.trim().toLowerCase(Locale.ROOT)); }
        sql.append(") matched WHERE hits > 0 ORDER BY hits DESC, document_id, chunk_index, chunk_id LIMIT ? OFFSET ?");
        args.add(limit); args.add(offset);
        return jdbc.query(sql.toString(), (rs, row) -> new SearchResult(rs.getLong("document_id"), rs.getString("file_name"), rs.getLong("chunk_id"),
            rs.getObject("page_number", Integer.class), rs.getObject("chunk_index", Integer.class), excerpt(rs.getString("content"), terms),
            (double)rs.getInt("hits")/terms.size()), args.toArray());
    }
    private static void validateFilter(String value, int max) {
        if (value != null && value.length() > max) throw new BusinessException(ErrorCode.BAD_REQUEST, "筛选条件过长");
    }
    private static String excerpt(String content, List<String> terms) {
        String lower = content.toLowerCase(Locale.ROOT);
        int at = terms.stream().mapToInt(lower::indexOf).filter(index -> index >= 0).min().orElse(0);
        int start = Math.max(0, at-80), end = Math.min(content.length(), start+320);
        return (start > 0 ? "…" : "") + content.substring(start,end) + (end < content.length() ? "…" : "");
    }
}
