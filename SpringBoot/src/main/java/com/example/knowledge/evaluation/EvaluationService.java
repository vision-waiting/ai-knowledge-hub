package com.example.knowledge.evaluation;

import com.example.knowledge.common.BusinessException;
import com.example.knowledge.common.ErrorCode;
import com.example.knowledge.entity.Document;
import com.example.knowledge.mapper.DocumentMapper;
import com.example.knowledge.rag.HybridRetriever;
import com.example.knowledge.rag.RetrievalResult;
import com.example.knowledge.security.JwtPrincipal;
import com.example.knowledge.service.PermissionService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class EvaluationService {
    private final PermissionService permissions;
    private final HybridRetriever retriever;
    private final DocumentMapper documents;
    private final JdbcTemplate jdbc;
    public EvaluationService(PermissionService permissions, HybridRetriever retriever, DocumentMapper documents, JdbcTemplate jdbc) {
        this.permissions = permissions; this.retriever = retriever; this.documents = documents; this.jdbc = jdbc;
    }

    public record DebugChunk(Long chunkId, Long documentId, String documentName, Integer pageNumber, Integer chunkIndex,
                             String content, double vectorScore, double keywordScore, double hybridScore) {}
    public record DebugResult(String question, int topK, boolean sufficient, long latencyMs, List<DebugChunk> chunks) {}
    public record CaseResult(String question, List<Long> expectedDocumentIds, List<Long> retrievedDocumentIds,
                             boolean sufficient, long latencyMs, Double hitAtK, Double reciprocalRank,
                             Double sourceRecall, Boolean correctRejection) {}
    public record EvaluationRun(int topK, int caseCount, int answerableCount, int unanswerableCount, Double hitAtK,
                                Double mrr, Double sourceRecall, Double unanswerableRejectionRate,
                                long latencyMs, List<CaseResult> cases, Long id, String datasetHash, LocalDateTime createdAt) {}
    public record RunSummary(long id, int topK, int caseCount, int answerableCount, int unanswerableCount, Double hitAtK, Double mrr,
                             Double sourceRecall, Double unanswerableRejectionRate, long latencyMs, String datasetHash, LocalDateTime createdAt) {}

    public DebugResult debug(Long kbId, JwtPrincipal user, EvaluationController.DebugRequest request) {
        permissions.requireKnowledgeBaseManagement(kbId, user);
        long started = System.nanoTime();
        RetrievalResult result = retriever.retrieve(kbId, request.question().trim(), request.topK());
        Map<Long, String> names = new HashMap<>();
        List<DebugChunk> chunks = result.items().stream().map(item -> {
            var chunk = item.chunk();
            String name = names.computeIfAbsent(chunk.getDocumentId(), id -> {
                Document document = documents.selectById(id);
                return document != null && kbId.equals(document.getKnowledgeBaseId()) ? document.getFileName() : "已删除文档";
            });
            return new DebugChunk(chunk.getId(), chunk.getDocumentId(), name, chunk.getPageNumber(), chunk.getChunkIndex(),
                    chunk.getContent(), item.vectorScore(), item.keywordScore(), item.hybridScore());
        }).toList();
        return new DebugResult(request.question().trim(), request.topK(), result.sufficient(), elapsed(started), chunks);
    }

    public EvaluationRun evaluate(Long kbId, JwtPrincipal user, EvaluationController.EvaluationRequest request) {
        permissions.requireKnowledgeBaseManagement(kbId, user);
        // Validate the entire dataset before paying for any remote embeddings.
        Set<Long> expectedIds = new HashSet<>();
        request.cases().forEach(item -> expectedIds.addAll(item.expectedDocumentIds()));
        for (Long id : expectedIds) {
            Document document = documents.selectById(id);
            if (document == null || !kbId.equals(document.getKnowledgeBaseId()) || !"SUCCESS".equals(document.getStatus()))
                throw new BusinessException(ErrorCode.BAD_REQUEST, "期望文档必须属于当前知识库且已索引成功");
        }
        long started = System.nanoTime();
        List<CaseResult> cases = new ArrayList<>();
        double hits = 0, reciprocalRanks = 0, recalls = 0;
        int answerable = 0, unanswerable = 0, rejected = 0;
        for (var item : request.cases()) {
            long caseStarted = System.nanoTime();
            RetrievalResult result = retriever.retrieve(kbId, item.question().trim(), request.topK());
            List<Long> actual = result.items().stream().map(hit -> hit.chunk().getDocumentId()).toList();
            Set<Long> expected = new LinkedHashSet<>(item.expectedDocumentIds());
            if (expected.isEmpty()) {
                unanswerable++;
                boolean correct = !result.sufficient();
                if (correct) rejected++;
                cases.add(new CaseResult(item.question().trim(), List.of(), actual, result.sufficient(), elapsed(caseStarted), null, null, null, correct));
            } else {
                answerable++;
                int firstRank = 0;
                Set<Long> matched = new HashSet<>();
                for (int i = 0; i < actual.size(); i++) if (expected.contains(actual.get(i))) {
                    if (firstRank == 0) firstRank = i + 1;
                    matched.add(actual.get(i));
                }
                double hit = firstRank > 0 ? 1 : 0;
                double reciprocalRank = firstRank > 0 ? 1.0 / firstRank : 0;
                double recall = (double) matched.size() / expected.size();
                hits += hit; reciprocalRanks += reciprocalRank; recalls += recall;
                cases.add(new CaseResult(item.question().trim(), List.copyOf(expected), actual, result.sufficient(), elapsed(caseStarted), hit, reciprocalRank, recall, null));
            }
        }
        Double hitAtK = average(hits, answerable), mrr = average(reciprocalRanks, answerable),
                recall = average(recalls, answerable), rejection = average(rejected, unanswerable);
        long latency = elapsed(started);
        String hash = datasetHash(request.cases());
        LocalDateTime createdAt = LocalDateTime.now().withNano(0);
        int answerableCount = answerable, unanswerableCount = unanswerable, caseCount = cases.size();
        var key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement("INSERT INTO retrieval_evaluation_run(knowledge_base_id,user_id,top_k,case_count,"
                    + "answerable_count,unanswerable_count,hit_at_k,mrr,source_recall,rejection_rate,latency_ms,dataset_hash,created_at) "
                    + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)", new String[]{"id"});
            statement.setLong(1, kbId); statement.setLong(2, user.userId()); statement.setInt(3, request.topK()); statement.setInt(4, caseCount);
            statement.setInt(5, answerableCount); statement.setInt(6, unanswerableCount); statement.setObject(7, hitAtK); statement.setObject(8, mrr);
            statement.setObject(9, recall); statement.setObject(10, rejection); statement.setLong(11, latency); statement.setString(12, hash);
            statement.setTimestamp(13, Timestamp.valueOf(createdAt));
            return statement;
        }, key);
        return new EvaluationRun(request.topK(), caseCount, answerable, unanswerable, hitAtK, mrr, recall, rejection, latency,
                List.copyOf(cases), key.getKey() == null ? null : key.getKey().longValue(), hash, createdAt);
    }

    /** Recent runs let maintainers compare retrieval quality over time; equal hashes mean the same labelled questions. */
    public List<RunSummary> runs(Long kbId, JwtPrincipal user) {
        permissions.requireKnowledgeBaseManagement(kbId, user);
        return jdbc.query("SELECT * FROM retrieval_evaluation_run WHERE knowledge_base_id=? ORDER BY created_at DESC, id DESC LIMIT 20",
                (rs, i) -> new RunSummary(rs.getLong("id"), rs.getInt("top_k"), rs.getInt("case_count"), rs.getInt("answerable_count"),
                        rs.getInt("unanswerable_count"), rs.getObject("hit_at_k", Double.class), rs.getObject("mrr", Double.class),
                        rs.getObject("source_recall", Double.class), rs.getObject("rejection_rate", Double.class), rs.getLong("latency_ms"),
                        rs.getString("dataset_hash"), rs.getTimestamp("created_at").toLocalDateTime()), kbId);
    }

    static String datasetHash(List<EvaluationController.EvaluationCase> cases) {
        StringBuilder value = new StringBuilder();
        for (var item : cases) value.append(item.question().trim()).append('\u0000').append(new TreeSet<>(item.expectedDocumentIds())).append('\n');
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0, 12);
        } catch (NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }
    private static Double average(double total, int count) { return count == 0 ? null : total / count; }
    private static long elapsed(long started) { return (System.nanoTime() - started) / 1_000_000; }
}
