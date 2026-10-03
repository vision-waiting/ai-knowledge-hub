package com.example.knowledge.intelligence;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.knowledge.common.BusinessException;
import com.example.knowledge.common.ErrorCode;
import com.example.knowledge.entity.Document;
import com.example.knowledge.entity.DocumentChunk;
import com.example.knowledge.mapper.DocumentMapper;
import com.example.knowledge.mapper.DocumentChunkMapper;
import com.example.knowledge.rag.RetrievedChunk;
import com.example.knowledge.security.JwtPrincipal;
import com.example.knowledge.service.PermissionService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;
import static com.example.knowledge.intelligence.IntelligenceModels.*;

@Service
public class IntelligenceService {
    private static final int CONTEXT_LIMIT = 20000;
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final PermissionService permissions;
    private final DocumentMapper documents;
    private final DocumentChunkMapper chunks;
    private final IntelligenceGenerator generator;

    public IntelligenceService(JdbcTemplate jdbc, ObjectMapper json, PermissionService permissions,
                               DocumentMapper documents, DocumentChunkMapper chunks, IntelligenceGenerator generator) {
        this.jdbc = jdbc; this.json = json; this.permissions = permissions;
        this.documents = documents; this.chunks = chunks; this.generator = generator;
    }
    record Sources(List<RetrievedChunk> context, List<SourceCoverage> coverage, String notice) {}

    public Analysis analyze(Long kb, AnalysisRequest request, JwtPrincipal user) {
        if (request == null || !("SUMMARY".equals(request.type()) || "COMPARE".equals(request.type()))) throw bad("分析类型不合法");
        validateIds(request.documentIds(), "COMPARE".equals(request.type()) ? 2 : 1);
        String focus = request.focus() == null ? "" : request.focus().strip();
        if (focus.length() > 500) throw bad("关注点不能超过 500 字");
        Sources source = sources(kb, request.documentIds(), user);
        var generated = generator.analyze(request.type(), focus, source.context(), source.coverage());
        // Recheck access and readiness after the external provider finishes.
        sources(kb, request.documentIds(), user);
        String title = ("COMPARE".equals(request.type()) ? "文档对比 · " : "文档摘要 · ") + source.coverage().size() + " 份资料";
        long id = insert("INSERT INTO document_analysis (user_id,knowledge_base_id,analysis_type,title,content,mode,focus,document_ids_json,sources_json,source_notice) VALUES (?,?,?,?,?,?,?,?,?,?)",
                user.userId(), kb, request.type(), title, generated.content(), generated.mode(), focus,
                encode(request.documentIds()), encode(source.coverage()), source.notice());
        return analysisRow(owned("document_analysis", id, user));
    }

    public List<Analysis> analyses(Long kb, JwtPrincipal user) {
        permissions.requireKnowledgeBaseAccess(kb, user, false);
        return jdbc.queryForList("SELECT * FROM document_analysis WHERE user_id=? AND knowledge_base_id=? ORDER BY created_at DESC,id DESC", user.userId(), kb)
                .stream().filter(row -> accessible(row, user)).map(this::analysisRow).toList();
    }

    public Study createStudy(Long kb, StudyRequest request, JwtPrincipal user) {
        if (request == null || request.questionCount() == null || request.questionCount() < 3 || request.questionCount() > 10)
            throw bad("题目数量必须为 3 至 10");
        validateIds(request.documentIds(), 1);
        Sources source = sources(kb, request.documentIds(), user);
        var generated = generator.study(request.questionCount(), source.context());
        sources(kb, request.documentIds(), user);
        String title = "学习练习 · " + source.coverage().getFirst().fileName();
        if (title.length() > 190) title = title.substring(0, 190);
        long id = insert("INSERT INTO study_session (user_id,knowledge_base_id,title,mode,document_ids_json,sources_json,source_notice,cards_json,questions_json) VALUES (?,?,?,?,?,?,?,?,?)",
                user.userId(), kb, title, generated.mode(), encode(request.documentIds()), encode(source.coverage()),
                source.notice(), encode(generated.cards()), encode(generated.questions()));
        return studyRow(owned("study_session", id, user));
    }

    public List<Study> studies(Long kb, JwtPrincipal user) {
        if (kb != null) permissions.requireKnowledgeBaseAccess(kb, user, false);
        List<Map<String, Object>> rows = kb == null
                ? jdbc.queryForList("SELECT * FROM study_session WHERE user_id=? ORDER BY created_at DESC,id DESC", user.userId())
                : jdbc.queryForList("SELECT * FROM study_session WHERE user_id=? AND knowledge_base_id=? ORDER BY created_at DESC,id DESC", user.userId(), kb);
        return rows.stream().filter(row -> accessible(row, user)).map(this::studyRow).toList();
    }

    public Study study(Long id, JwtPrincipal user) { return studyRow(checkedStudy(id, user)); }

    @Transactional
    public Grade submit(Long id, SubmitRequest request, JwtPrincipal user) {
        // Serialize attempts for this session so a correct retry consistently resolves an older mistake.
        Map<String, Object> row = ownedLockedStudy(id, user);
        checkSources(row, user);
        List<StoredQuestion> questions = decode(row.get("questions_json"), StoredQuestion.class);
        if (request == null || request.answers() == null || request.answers().size() != questions.size()) throw bad("请回答全部题目");
        List<GradedQuestion> results = new ArrayList<>();
        int score = 0;
        for (int i = 0; i < questions.size(); i++) {
            StoredQuestion q = questions.get(i);
            Integer selected = request.answers().get(i);
            if (selected == null || selected < 0 || selected >= q.options().size()) throw bad("答案选项不合法");
            boolean correct = selected == q.correctIndex();
            if (correct) score++;
            results.add(new GradedQuestion(q.id(), q.prompt(), q.options(), selected, q.correctIndex(), q.explanation(), correct));
        }
        long attemptId = insert("INSERT INTO study_attempt (study_id,user_id,score,total,results_json) VALUES (?,?,?,?,?)",
                id, user.userId(), score, questions.size(), encode(results));
        for (GradedQuestion result : results) {
            if (result.correct()) {
                jdbc.update("UPDATE study_mistake SET resolved=1,updated_at=CURRENT_TIMESTAMP WHERE study_id=? AND user_id=? AND question_id=?",
                        id, user.userId(), result.questionId());
            } else {
                int updated = jdbc.update("UPDATE study_mistake SET result_json=?,resolved=0,updated_at=CURRENT_TIMESTAMP WHERE study_id=? AND user_id=? AND question_id=?",
                        encode(result), id, user.userId(), result.questionId());
                if (updated == 0) jdbc.update("INSERT INTO study_mistake (study_id,user_id,question_id,result_json) VALUES (?,?,?,?)",
                        id, user.userId(), result.questionId(), encode(result));
            }
        }
        return gradeRow(owned("study_attempt", attemptId, user));
    }

    public List<Grade> attempts(Long id, JwtPrincipal user) {
        checkedStudy(id, user);
        return jdbc.queryForList("SELECT * FROM study_attempt WHERE study_id=? AND user_id=? ORDER BY created_at DESC,id DESC", id, user.userId())
                .stream().map(this::gradeRow).toList();
    }

    public List<Mistake> mistakes(boolean includeResolved, JwtPrincipal user) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT m.*,s.title FROM study_mistake m JOIN study_session s ON s.id=m.study_id AND s.user_id=m.user_id WHERE m.user_id=?"
                + (includeResolved ? "" : " AND m.resolved=0") + " ORDER BY m.updated_at DESC,m.id DESC", user.userId());
        List<Mistake> result = new ArrayList<>();
        Map<Long, Boolean> access = new HashMap<>();
        for (Map<String, Object> row : rows) {
            long studyId = number(row, "study_id");
            boolean allowed = access.computeIfAbsent(studyId, key -> {
                try { checkedStudy(key, user); return true; }
                catch (BusinessException e) { return false; }
            });
            if (!allowed) continue;
            GradedQuestion q = decodeOne(row.get("result_json"), GradedQuestion.class);
            result.add(new Mistake(number(row, "id"), studyId, string(row, "title"), q.questionId(), q.prompt(), q.options(),
                    q.selectedIndex(), q.correctIndex(), q.explanation(), false, flag(row.get("resolved")), time(row, "created_at")));
        }
        return result;
    }

    @Transactional
    public void resolve(Long id, ResolveRequest request, JwtPrincipal user) {
        if (request == null || request.resolved() == null) throw bad("必须提供 resolved 状态");
        Map<String, Object> row = owned("study_mistake", id, user);
        checkSources(ownedLockedStudy(number(row, "study_id"), user), user);
        jdbc.update("UPDATE study_mistake SET resolved=?,updated_at=CURRENT_TIMESTAMP WHERE id=? AND user_id=?", request.resolved() ? 1 : 0, id, user.userId());
    }

    private void validateIds(List<Long> ids, int min) {
        if (ids == null || ids.size() < min || ids.size() > 5 || ids.stream().anyMatch(id -> id == null || id <= 0)
                || new HashSet<>(ids).size() != ids.size()) throw bad("请选择 " + min + " 至 5 份不同文档");
    }

    private Sources sources(Long kb, List<Long> ids, JwtPrincipal user) {
        permissions.requireKnowledgeBaseAccess(kb, user, false);
        List<Document> docs = new ArrayList<>();
        List<List<DocumentChunk>> all = new ArrayList<>();
        for (Long id : ids) {
            Document doc = documents.selectById(id);
            if (doc == null || !kb.equals(doc.getKnowledgeBaseId())) throw new BusinessException(ErrorCode.NOT_FOUND, "来源文档不存在或不属于该知识库");
            if (!"SUCCESS".equals(doc.getStatus())) throw new BusinessException(ErrorCode.CONFLICT, "来源文档尚未解析完成");
            List<DocumentChunk> documentChunks = chunks.selectList(new LambdaQueryWrapper<DocumentChunk>()
                    .eq(DocumentChunk::getDocumentId, id).eq(DocumentChunk::getKnowledgeBaseId, kb)
                    .orderByAsc(DocumentChunk::getChunkIndex).orderByAsc(DocumentChunk::getId));
            List<DocumentChunk> usable = documentChunks.stream().filter(c -> c.getContent() != null && !c.getContent().isBlank()).toList();
            if (usable.isEmpty()) throw new BusinessException(ErrorCode.INSUFFICIENT_CONTEXT, "来源文档没有可用正文");
            docs.add(doc); all.add(usable);
        }
        // Water-filling allocates equally among documents; unused capacity from short documents is redistributed.
        int[] totals = new int[ids.size()];
        for (int i = 0; i < ids.size(); i++) totals[i] = all.get(i).stream().mapToInt(c -> c.getContent().length()).sum();
        int[] budget = allocate(totals, CONTEXT_LIMIT);
        List<RetrievedChunk> context = new ArrayList<>();
        List<SourceCoverage> coverage = new ArrayList<>();
        for (int i = 0; i < ids.size(); i++) {
            List<DocumentChunk> original = all.get(i);
            int[] chunkBudgets = allocate(original.stream().mapToInt(c -> c.getContent().length()).toArray(), budget[i]);
            int remaining = budget[i];
            int included = 0;
            for (int c = 0; c < original.size() && remaining > 0; c++) {
                DocumentChunk chunk = original.get(c);
                int take = chunkBudgets[c];
                if (take == 0) continue;
                DocumentChunk copy = new DocumentChunk();
                copy.setId(chunk.getId()); copy.setDocumentId(chunk.getDocumentId()); copy.setKnowledgeBaseId(kb);
                copy.setPageNumber(chunk.getPageNumber()); copy.setChunkIndex(chunk.getChunkIndex());
                copy.setContent(chunk.getContent().substring(0, take));
                context.add(new RetrievedChunk(copy, 1, 1, 1)); remaining -= take; included++;
            }
            coverage.add(new SourceCoverage(ids.get(i), docs.get(i).getFileName(), included, original.size(), budget[i] - remaining, totals[i]));
        }
        int total = Arrays.stream(totals).sum();
        int included = coverage.stream().mapToInt(SourceCoverage::includedCharacters).sum();
        String notice = "本次使用 " + coverage.size() + " 份文档，共 " + included + " / " + total + " 字。"
                + (included < total ? "受 20,000 字上下文限制，已在文档之间公平分配并抽取分段正文；结果仅覆盖所选片段，不能视为全文结论。" : "已包含所选文档的全部可用正文。")
                ;
        return new Sources(context, coverage, notice);
    }

    private static int[] allocate(int[] capacities, int remaining) {
        int[] allocations = new int[capacities.length];
        while (remaining > 0) {
            int active = 0;
            for (int i = 0; i < capacities.length; i++) if (allocations[i] < capacities[i]) active++;
            if (active == 0) break;
            int share = Math.max(1, remaining / active);
            for (int i = 0; i < capacities.length && remaining > 0; i++) {
                int take = Math.min(remaining, Math.min(share, capacities[i] - allocations[i]));
                allocations[i] += take; remaining -= take;
            }
        }
        return allocations;
    }

    private Map<String, Object> checkedStudy(Long id, JwtPrincipal user) {
        Map<String, Object> row = owned("study_session", id, user); checkSources(row, user); return row;
    }
    private void checkSources(Map<String, Object> row, JwtPrincipal user) {
        long kb = number(row, "knowledge_base_id");
        permissions.requireKnowledgeBaseAccess(kb, user, false);
        for (Long id : decode(row.get("document_ids_json"), Long.class)) {
            Document doc = documents.selectById(id);
            if (doc == null || !Long.valueOf(kb).equals(doc.getKnowledgeBaseId()))
                throw new BusinessException(ErrorCode.NOT_FOUND, "来源文档不存在或不属于该知识库");
            if (!List.of("SUCCESS", "ARCHIVED", "TRASHED").contains(doc.getStatus()))
                throw new BusinessException(ErrorCode.CONFLICT, "来源文档尚未解析完成");
        }
    }
    private boolean accessible(Map<String, Object> row, JwtPrincipal user) {
        try { checkSources(row, user); return true; }
        catch (BusinessException e) { return false; }
    }
    private Map<String, Object> owned(String table, Long id, JwtPrincipal user) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM " + table + " WHERE id=? AND user_id=?", id, user.userId());
        if (rows.isEmpty()) throw new BusinessException(ErrorCode.NOT_FOUND);
        return rows.getFirst();
    }
    private Map<String, Object> ownedLockedStudy(Long id, JwtPrincipal user) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM study_session WHERE id=? AND user_id=? FOR UPDATE", id, user.userId());
        if (rows.isEmpty()) throw new BusinessException(ErrorCode.NOT_FOUND);
        return rows.getFirst();
    }
    private Analysis analysisRow(Map<String, Object> r) {
        return new Analysis(number(r,"id"), number(r,"knowledge_base_id"), string(r,"analysis_type"), string(r,"title"), string(r,"content"),
                string(r,"mode"), time(r,"created_at"), decode(r.get("document_ids_json"), Long.class), string(r,"focus"),
                string(r,"source_notice"), decode(r.get("sources_json"), SourceCoverage.class));
    }
    private Study studyRow(Map<String, Object> r) {
        List<StoredQuestion> questions = decode(r.get("questions_json"), StoredQuestion.class);
        return new Study(number(r,"id"), number(r,"knowledge_base_id"), string(r,"title"), string(r,"mode"),
                decode(r.get("cards_json"), Card.class), questions.stream().map(StoredQuestion::publicView).toList(), time(r,"created_at"),
                string(r,"source_notice"), decode(r.get("sources_json"), SourceCoverage.class));
    }
    private Grade gradeRow(Map<String, Object> r) {
        return new Grade(number(r,"id"), (int) number(r,"score"), (int) number(r,"total"), decode(r.get("results_json"), GradedQuestion.class), time(r,"created_at"));
    }
    private long insert(String sql, Object... values) {
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement(sql, new String[]{"id"});
            for (int i = 0; i < values.length; i++) statement.setObject(i + 1, values[i]);
            return statement;
        }, keys);
        return Objects.requireNonNull(keys.getKey()).longValue();
    }
    private String encode(Object value) {
        try { return json.writeValueAsString(value); }
        catch (JsonProcessingException e) { throw new IllegalStateException("Cannot store intelligence record", e); }
    }
    private <T> List<T> decode(Object value, Class<T> type) {
        try { return json.readValue(value.toString(), json.getTypeFactory().constructCollectionType(List.class, type)); }
        catch (JsonProcessingException e) { throw new IllegalStateException("Invalid stored intelligence record", e); }
    }
    private <T> T decodeOne(Object value, Class<T> type) {
        try { return json.readValue(value.toString(), type); }
        catch (JsonProcessingException e) { throw new IllegalStateException("Invalid stored intelligence record", e); }
    }
    private static long number(Map<String, Object> row, String key) { return ((Number) row.get(key)).longValue(); }
    private static String string(Map<String, Object> row, String key) { return row.get(key) == null ? "" : row.get(key).toString(); }
    private static boolean flag(Object value) { return value instanceof Boolean b ? b : ((Number) value).intValue() != 0; }
    private static LocalDateTime time(Map<String, Object> row, String key) {
        Object value = row.get(key); return value instanceof Timestamp t ? t.toLocalDateTime() : (LocalDateTime) value;
    }
    private BusinessException bad(String message) { return new BusinessException(ErrorCode.BAD_REQUEST, message); }
}
