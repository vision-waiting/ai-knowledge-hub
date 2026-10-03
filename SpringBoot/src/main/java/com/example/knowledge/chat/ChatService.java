package com.example.knowledge.chat;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.knowledge.common.BusinessException;
import com.example.knowledge.common.ErrorCode;
import com.example.knowledge.entity.AiUsageLog;
import com.example.knowledge.entity.ChatMessage;
import com.example.knowledge.entity.ChatSession;
import com.example.knowledge.mapper.AiUsageLogMapper;
import com.example.knowledge.mapper.ChatMessageMapper;
import com.example.knowledge.mapper.ChatSessionMapper;
import com.example.knowledge.rag.*;
import com.example.knowledge.security.JwtPrincipal;
import com.example.knowledge.service.PermissionService;
import com.example.knowledge.vo.ChatMessageVO;
import com.example.knowledge.vo.ChatSessionVO;
import com.example.knowledge.dto.ChatFeedbackRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.IntStream;

@Service
public class ChatService {
    private final HybridRetriever retriever;
    private final AiService aiService;
    private final CitationBuilder citationBuilder;
    private final ChatSessionMapper sessionMapper;
    private final ChatMessageMapper messageMapper;
    private final QueryRewriteService rewriteService;
    private final PermissionService permissionService;
    private final AiUsageLogMapper aiUsageLogMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Set<Long> activeSessions = ConcurrentHashMap.newKeySet();
    private static final int RETRIEVAL_TOP_K = 5;
    private TransactionTemplate transactions;

    public ChatService(HybridRetriever retriever, AiService aiService, CitationBuilder citationBuilder, ChatSessionMapper sessionMapper,
                       ChatMessageMapper messageMapper, QueryRewriteService rewriteService, PermissionService permissionService) {
        this(retriever, aiService, citationBuilder, sessionMapper, messageMapper, rewriteService, permissionService, null);
    }

    @Autowired
    public ChatService(HybridRetriever retriever, AiService aiService, CitationBuilder citationBuilder, ChatSessionMapper sessionMapper,
                       ChatMessageMapper messageMapper, QueryRewriteService rewriteService, PermissionService permissionService,
                       AiUsageLogMapper aiUsageLogMapper) {
        this.retriever = retriever; this.aiService = aiService; this.citationBuilder = citationBuilder;
        this.sessionMapper = sessionMapper; this.messageMapper = messageMapper; this.rewriteService = rewriteService;
        this.permissionService = permissionService; this.aiUsageLogMapper = aiUsageLogMapper;
    }

    @Autowired
    void setTransactionManager(PlatformTransactionManager manager) { this.transactions = new TransactionTemplate(manager); }

    public ChatAnswer answer(Long knowledgeBaseId, String question, JwtPrincipal user, Long sessionId) {
        return answerStream(knowledgeBaseId, question, user, sessionId, ignored -> {}, () -> false);
    }

    public ChatAnswer answerStream(Long knowledgeBaseId, String question, JwtPrincipal user, Long sessionId,
                                  Consumer<String> onChunk, BooleanSupplier cancelled) {
        return answerStream(knowledgeBaseId, question, user, sessionId, onChunk, cancelled, ignored -> {});
    }

    public ChatAnswer answerStream(Long knowledgeBaseId, String question, JwtPrincipal user, Long sessionId,
                                  Consumer<String> onChunk, BooleanSupplier cancelled, Consumer<ChatMetadata> onMetadata) {
        validate(knowledgeBaseId, question, user);
        checkCancelled(cancelled);
        String normalizedQuestion = question.trim();
        Long actualSessionId = inTransaction(() -> ensureSession(knowledgeBaseId, normalizedQuestion, user, sessionId));
        if (!activeSessions.add(actualSessionId)) throw new BusinessException(ErrorCode.CONFLICT, "该会话正在生成回答，请稍后重试");
        try {
            List<ChatMessage> history = messageMapper.selectList(new QueryWrapper<ChatMessage>()
                    .eq("session_id", actualSessionId).orderByDesc("id").last("limit 10"));
            history = history == null ? List.of() : history;
            String rewritten = rewriteService.rewriteConversation(normalizedQuestion, history);
            List<ConversationMessage> conversation = conversation(history);
            checkCancelled(cancelled);
            inTransaction(() -> { saveMessage(actualSessionId, "USER", normalizedQuestion, null, List.of(), null); touchSession(actualSessionId); return null; });
            String smallTalkReply = SmallTalk.reply(normalizedQuestion);
            long retrievalStarted = System.nanoTime();
            RetrievalResult retrieval = smallTalkReply == null
                    ? retriever.retrieve(knowledgeBaseId, rewritten, RETRIEVAL_TOP_K) : new RetrievalResult(List.of(), false);
            RetrievalTrace trace = smallTalkReply == null
                    ? RetrievalTrace.of(rewritten, RETRIEVAL_TOP_K, retrieval, (System.nanoTime() - retrievalStarted) / 1_000_000) : null;
            checkCancelled(cancelled);
            String answer;
            String mode;
            List<Citation> citations;
            if (smallTalkReply != null) {
                answer = smallTalkReply;
                mode = "SMALL_TALK";
                citations = List.of();
                onChunk.accept(answer);
            } else if (!retrieval.sufficient()) {
                answer = AiService.INSUFFICIENT_CONTEXT;
                mode = "INSUFFICIENT_CONTEXT";
                citations = List.of();
                onChunk.accept(answer);
            } else {
                long started = System.currentTimeMillis();
                try {
                    StringBuilder streamed = new StringBuilder();
                    AiResponse response = aiService.stream(new AiRequest(normalizedQuestion, retrieval.items(), true, conversation), fragment -> {
                        checkCancelled(cancelled);
                        streamed.append(fragment);
                        onChunk.accept(fragment);
                    }, cancelled);
                    checkCancelled(cancelled);
                    answer = streamed.isEmpty() ? response.answer() : streamed.toString();
                    mode = response.mode();
                    if (answer == null || answer.isBlank()) throw new BusinessException(ErrorCode.AI_UNAVAILABLE);
                    if (streamed.isEmpty()) onChunk.accept(answer);
                    recordAiUsage(user, mode, System.currentTimeMillis() - started, true, null);
                } catch (RuntimeException exception) {
                    recordAiUsage(user, aiService.mode(), System.currentTimeMillis() - started, false,
                            exception instanceof CancellationException ? "请求已取消" : "AI 请求失败");
                    throw exception;
                }
                citations = IntStream.range(0, Math.min(5, retrieval.items().size()))
                        .mapToObj(index -> citationBuilder.build(retrieval.items().get(index).chunk(), index + 1)).toList();
            }
            checkCancelled(cancelled);
            Long savedAnswerId = inTransaction(() -> {
                requireSession(actualSessionId, user);
                Long messageId = saveMessage(actualSessionId, "ASSISTANT", answer, mode, citations, trace);
                touchSession(actualSessionId);
                return messageId;
            });
            onMetadata.accept(new ChatMetadata(actualSessionId, mode, savedAnswerId, trace));
            return new ChatAnswer(actualSessionId, answer, mode, citations);
        } finally { activeSessions.remove(actualSessionId); }
    }

    public void validate(Long knowledgeBaseId, String question, JwtPrincipal user) {
        if (user == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);
        if (knowledgeBaseId == null || knowledgeBaseId < 1 || question == null || question.isBlank() || question.length() > 4000)
            throw new BusinessException(ErrorCode.BAD_REQUEST, "问题不能为空且不能超过 4000 字");
        permissionService.requireKnowledgeBaseAccess(knowledgeBaseId, user, false);
    }

    public List<ChatSessionVO> listSessions(JwtPrincipal user) {
        List<ChatSession> sessions = sessionMapper.selectList(new QueryWrapper<ChatSession>()
                .eq("user_id", user.userId()).orderByDesc("updated_at", "id"));
        return sessions == null ? List.of() : sessions.stream().map(item -> new ChatSessionVO(item.getId(), item.getUserId(),
                item.getKnowledgeBaseId(), item.getTitle(), item.getCreatedAt(), item.getUpdatedAt())).toList();
    }

    public List<ChatMessageVO> listMessages(Long sessionId, JwtPrincipal user) {
        ChatSession session = requireSession(sessionId, user);
        permissionService.requireKnowledgeBaseAccess(session.getKnowledgeBaseId(), user, false);
        List<ChatMessage> messages = messageMapper.selectList(new QueryWrapper<ChatMessage>().eq("session_id", sessionId).orderByAsc("id"));
        return messages == null ? List.of() : messages.stream().map(item -> new ChatMessageVO(item.getId(), item.getSessionId(),
                item.getRole(), item.getContent(), item.getTokenCount(), item.getCreatedAt(), item.getMode(), readCitations(item.getCitationsJson()), item.getFeedback(), item.getFeedbackReason(),
                readTrace(item.getRetrievalJson()))).toList();
    }

    public void deleteSession(Long sessionId, JwtPrincipal user) {
        requireSession(sessionId, user);
        if (!activeSessions.add(sessionId)) throw new BusinessException(ErrorCode.CONFLICT, "该会话正在生成回答，请稍后重试");
        try {
            inTransaction(() -> {
                requireSession(sessionId, user);
                messageMapper.delete(new QueryWrapper<ChatMessage>().eq("session_id", sessionId));
                sessionMapper.deleteById(sessionId);
                return null;
            });
        } finally { activeSessions.remove(sessionId); }
    }

    public void feedback(Long messageId, ChatFeedbackRequest request, JwtPrincipal user) {
        ChatMessage message = messageMapper.selectById(messageId);
        if (message == null || !"ASSISTANT".equals(message.getRole())) throw new BusinessException(ErrorCode.NOT_FOUND);
        ChatSession session = requireSession(message.getSessionId(), user);
        permissionService.requireKnowledgeBaseAccess(session.getKnowledgeBaseId(), user, false);
        String value = request.feedback().trim().toUpperCase(Locale.ROOT);
        if (!Set.of("HELPFUL", "NOT_HELPFUL").contains(value)) throw new BusinessException(ErrorCode.BAD_REQUEST, "反馈类型不合法");
        messageMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<ChatMessage>()
                .eq("id", messageId).set("feedback", value).set("feedback_reason", request.reason())
                .set("feedback_status", "NOT_HELPFUL".equals(value) ? "OPEN" : null));
    }

    private Long ensureSession(Long knowledgeBaseId, String question, JwtPrincipal user, Long sessionId) {
        if (sessionId != null) {
            ChatSession existing = requireSession(sessionId, user);
            if (!knowledgeBaseId.equals(existing.getKnowledgeBaseId()))
                throw new BusinessException(ErrorCode.BAD_REQUEST, "会话不属于当前知识库");
            return sessionId;
        }
        ChatSession session = new ChatSession();
        session.setUserId(user.userId()); session.setKnowledgeBaseId(knowledgeBaseId);
        session.setTitle(question.substring(0, question.offsetByCodePoints(0, Math.min(question.codePointCount(0, question.length()), 60))));
        session.setCreatedAt(LocalDateTime.now()); session.setUpdatedAt(session.getCreatedAt());
        sessionMapper.insert(session);
        if (session.getId() == null) throw new IllegalStateException("Session creation did not return an ID");
        return session.getId();
    }

    private ChatSession requireSession(Long sessionId, JwtPrincipal user) {
        ChatSession session = sessionMapper.selectById(sessionId);
        if (session == null) throw new BusinessException(ErrorCode.NOT_FOUND);
        if (user == null || !user.userId().equals(session.getUserId())) throw new BusinessException(ErrorCode.FORBIDDEN);
        return session;
    }

    private List<ConversationMessage> conversation(List<ChatMessage> descendingHistory) {
        List<ChatMessage> chronological = new ArrayList<>(descendingHistory);
        Collections.reverse(chronological);
        return chronological.stream().filter(message -> "USER".equals(message.getRole()) || "ASSISTANT".equals(message.getRole()))
                .filter(message -> message.getContent() != null && !message.getContent().isBlank())
                .map(message -> new ConversationMessage(message.getRole().toLowerCase(Locale.ROOT),
                        message.getContent().substring(0, Math.min(message.getContent().length(), 4000)))).toList();
    }

    private Long saveMessage(Long sessionId, String role, String content, String mode, List<Citation> citations, RetrievalTrace trace) {
        ChatMessage message = new ChatMessage(); message.setSessionId(sessionId); message.setRole(role); message.setContent(content);
        message.setMode(mode); message.setCreatedAt(LocalDateTime.now());
        try {
            message.setCitationsJson(objectMapper.writeValueAsString(citations));
            if (trace != null) message.setRetrievalJson(objectMapper.writeValueAsString(trace));
        }
        catch (JsonProcessingException exception) { throw new IllegalStateException("Could not serialize citations", exception); }
        messageMapper.insert(message);
        return message.getId();
    }

    private List<Citation> readCitations(String json) {
        if (json == null || json.isBlank()) return List.of();
        try { return objectMapper.readValue(json, new TypeReference<List<Citation>>() {}); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("Stored citations are invalid", exception); }
    }

    /** The trace is diagnostic only: an unreadable value must never hide the conversation itself. */
    private RetrievalTrace readTrace(String json) {
        if (json == null || json.isBlank()) return null;
        try { return objectMapper.readValue(json, RetrievalTrace.class); }
        catch (JsonProcessingException exception) { return null; }
    }

    private void touchSession(Long sessionId) {
        ChatSession update = new ChatSession(); update.setId(sessionId); update.setUpdatedAt(LocalDateTime.now()); sessionMapper.updateById(update);
    }

    private void recordAiUsage(JwtPrincipal user, String mode, long duration, boolean success, String error) {
        if (aiUsageLogMapper == null) return;
        AiUsageLog log = new AiUsageLog(); log.setUserId(user.userId()); log.setMode(mode); log.setDurationMs(duration);
        log.setSuccess(success ? 1 : 0); log.setErrorMessage(error); aiUsageLogMapper.insert(log);
    }

    private <T> T inTransaction(Supplier<T> work) { return transactions == null ? work.get() : transactions.execute(status -> work.get()); }

    private static void checkCancelled(BooleanSupplier cancelled) {
        if (cancelled.getAsBoolean() || Thread.currentThread().isInterrupted()) throw new CancellationException("Chat request cancelled");
    }
}
