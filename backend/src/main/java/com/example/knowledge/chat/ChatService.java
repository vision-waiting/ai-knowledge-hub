package com.example.knowledge.chat;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.knowledge.entity.ChatMessage;
import com.example.knowledge.entity.ChatSession;
import com.example.knowledge.mapper.ChatMessageMapper;
import com.example.knowledge.mapper.ChatSessionMapper;
import com.example.knowledge.mapper.AiUsageLogMapper;
import com.example.knowledge.entity.AiUsageLog;
import com.example.knowledge.rag.AiRequest;
import com.example.knowledge.rag.AiResponse;
import com.example.knowledge.rag.AiService;
import com.example.knowledge.rag.Citation;
import com.example.knowledge.rag.CitationBuilder;
import com.example.knowledge.rag.HybridRetriever;
import com.example.knowledge.rag.RetrievalResult;
import com.example.knowledge.security.JwtPrincipal;
import com.example.knowledge.service.PermissionService;
import com.example.knowledge.vo.ChatSessionVO;
import com.example.knowledge.vo.ChatMessageVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ChatService {
    private final HybridRetriever retriever; private final AiService aiService; private final CitationBuilder citationBuilder;
    private final ChatSessionMapper sessionMapper; private final ChatMessageMapper messageMapper; private final QueryRewriteService rewriteService;
    private final PermissionService permissionService;
    private final AiUsageLogMapper aiUsageLogMapper;

    public ChatService(HybridRetriever retriever, AiService aiService, CitationBuilder citationBuilder, ChatSessionMapper sessionMapper,
                       ChatMessageMapper messageMapper, QueryRewriteService rewriteService, PermissionService permissionService) {
        this(retriever, aiService, citationBuilder, sessionMapper, messageMapper, rewriteService, permissionService, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public ChatService(HybridRetriever retriever, AiService aiService, CitationBuilder citationBuilder, ChatSessionMapper sessionMapper,
                       ChatMessageMapper messageMapper, QueryRewriteService rewriteService, PermissionService permissionService,
                       AiUsageLogMapper aiUsageLogMapper) {
        this.retriever = retriever; this.aiService = aiService; this.citationBuilder = citationBuilder; this.sessionMapper = sessionMapper; this.messageMapper = messageMapper; this.rewriteService = rewriteService;
        this.permissionService = permissionService;
        this.aiUsageLogMapper = aiUsageLogMapper;
    }

    @Transactional
    public ChatAnswer answer(Long knowledgeBaseId, String question, JwtPrincipal user, Long sessionId) {
        permissionService.requireKnowledgeBaseAccess(knowledgeBaseId, user, false);
        Long actualSessionId = ensureSession(knowledgeBaseId, question, user, sessionId);
        List<ChatMessage> historyMessages = messageMapper.selectList(new QueryWrapper<ChatMessage>().eq("session_id", actualSessionId).orderByDesc("created_at").last("limit 10"));
        List<String> history = historyMessages == null ? List.of() : historyMessages.stream().filter(item -> "USER".equals(item.getRole())).map(ChatMessage::getContent).toList();
        String rewritten = rewriteService.rewrite(question, history);
        saveMessage(actualSessionId, "USER", question);
        RetrievalResult retrieval = retriever.retrieve(knowledgeBaseId, rewritten, 5);
        String answer; String mode;
        if (!retrieval.sufficient()) {
            answer = "当前知识库中没有找到足够相关的资料，因此无法基于当前知识库准确回答该问题。"; mode = "LOCAL_DEMO";
        } else {
            long started = System.currentTimeMillis();
            try {
                AiResponse response = aiService.generate(new AiRequest(rewritten, retrieval.items(), true)); answer = response.answer(); mode = response.mode();
                recordAiUsage(user, mode, System.currentTimeMillis() - started, true, null);
            } catch (RuntimeException exception) {
                recordAiUsage(user, "ERROR", System.currentTimeMillis() - started, false, exception.getMessage()); throw exception;
            }
        }
        List<Citation> citations = retrieval.items().stream().limit(5).map(item -> citationBuilder.build(item.chunk(), retrieval.items().indexOf(item) + 1)).toList();
        saveMessage(actualSessionId, "ASSISTANT", answer);
        return new ChatAnswer(actualSessionId, answer, mode, citations);
    }

    public List<ChatSessionVO> listSessions(JwtPrincipal user) {
        List<ChatSession> sessions = sessionMapper.selectList(new QueryWrapper<ChatSession>().eq("user_id", user.userId()).orderByDesc("updated_at"));
        return sessions == null ? List.of() : sessions.stream().map(item -> new ChatSessionVO(item.getId(), item.getUserId(), item.getKnowledgeBaseId(), item.getTitle(), item.getCreatedAt(), item.getUpdatedAt())).toList();
    }

    public List<ChatMessageVO> listMessages(Long sessionId, JwtPrincipal user) {
        ChatSession session = sessionMapper.selectById(sessionId);
        if (session == null || !user.userId().equals(session.getUserId())) throw new com.example.knowledge.common.BusinessException(com.example.knowledge.common.ErrorCode.FORBIDDEN);
        List<ChatMessage> messages = messageMapper.selectList(new QueryWrapper<ChatMessage>().eq("session_id", sessionId).orderByAsc("created_at"));
        return messages == null ? List.of() : messages.stream().map(item -> new ChatMessageVO(item.getId(), item.getSessionId(), item.getRole(), item.getContent(), item.getTokenCount(), item.getCreatedAt())).toList();
    }

    private Long ensureSession(Long knowledgeBaseId, String question, JwtPrincipal user, Long sessionId) {
        if (sessionId != null) {
            ChatSession existing = sessionMapper.selectById(sessionId);
            if (existing != null && user.userId().equals(existing.getUserId()) && knowledgeBaseId.equals(existing.getKnowledgeBaseId())) return sessionId;
        }
        ChatSession session = new ChatSession(); session.setUserId(user.userId()); session.setKnowledgeBaseId(knowledgeBaseId); session.setTitle(question == null ? "新会话" : question.substring(0, Math.min(question.length(), 60))); sessionMapper.insert(session);
        return session.getId() == null ? 0L : session.getId();
    }

    private void saveMessage(Long sessionId, String role, String content) { ChatMessage message = new ChatMessage(); message.setSessionId(sessionId); message.setRole(role); message.setContent(content == null ? "" : content); message.setTokenCount(message.getContent().length()); messageMapper.insert(message); }

    private void recordAiUsage(JwtPrincipal user, String mode, long duration, boolean success, String error) {
        if (aiUsageLogMapper == null) return;
        AiUsageLog log = new AiUsageLog(); log.setUserId(user.userId()); log.setMode(mode); log.setDurationMs(duration); log.setSuccess(success ? 1 : 0); log.setErrorMessage(error); aiUsageLogMapper.insert(log);
    }
}
