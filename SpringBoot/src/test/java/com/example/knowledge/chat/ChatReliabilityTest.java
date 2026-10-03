package com.example.knowledge.chat;

import com.example.knowledge.common.BusinessException;
import com.example.knowledge.entity.ChatMessage;
import com.example.knowledge.entity.ChatSession;
import com.example.knowledge.entity.DocumentChunk;
import com.example.knowledge.mapper.ChatMessageMapper;
import com.example.knowledge.mapper.ChatSessionMapper;
import com.example.knowledge.rag.*;
import com.example.knowledge.security.JwtPrincipal;
import com.example.knowledge.service.PermissionService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.mockito.ArgumentCaptor;
import com.example.knowledge.vo.ChatMessageVO;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ChatReliabilityTest {
    private final HybridRetriever retriever = mock(HybridRetriever.class);
    private final AiService ai = mock(AiService.class);
    private final CitationBuilder citations = mock(CitationBuilder.class);
    private final ChatSessionMapper sessions = mock(ChatSessionMapper.class);
    private final ChatMessageMapper messages = mock(ChatMessageMapper.class);
    private final PermissionService permissions = mock(PermissionService.class);
    private final JwtPrincipal user = new JwtPrincipal(7L, "student", "USER");
    private final ChatService service = new ChatService(retriever, ai, citations, sessions, messages, new QueryRewriteService(), permissions);

    @Test
    void rejectsForeignSessionWithoutStartingAnotherOne() {
        existing(99L, 3L);
        assertThrows(BusinessException.class, () -> service.answer(3L, "question", user, 2L));
        verify(sessions, never()).insert(any(ChatSession.class));
        verifyNoInteractions(retriever, messages);
    }

    @Test
    void rejectsSessionFromDifferentKnowledgeBase() {
        existing(7L, 8L);
        assertThrows(BusinessException.class, () -> service.answer(3L, "question", user, 2L));
        verify(sessions, never()).insert(any(ChatSession.class));
        verifyNoInteractions(retriever, messages);
    }

    @Test
    void refusalHasNoIrrelevantCitations() {
        existing(7L, 3L);
        DocumentChunk chunk = new DocumentChunk(); chunk.setId(6L); chunk.setKnowledgeBaseId(3L);
        when(retriever.retrieve(3L, "unknown", 5)).thenReturn(new RetrievalResult(List.of(new RetrievedChunk(chunk, .01, 0, .007)), false));
        when(citations.build(chunk, 1)).thenReturn(new Citation(1, 1L, "irrelevant", 1, 6L, "irrelevant"));
        ChatAnswer result = service.answer(3L, "unknown", user, 2L);
        assertTrue(result.citations().isEmpty());
        verify(ai, never()).generate(any());
        verify(ai, never()).stream(any(), any(), any());
    }

    @Test
    void alreadyCancelledRequestDoesNotPersistMessagesOrStartRetrieval() {
        existing(7L, 3L);
        assertThrows(CancellationException.class,
                () -> service.answerStream(3L, "question", user, 2L, ignored -> {}, () -> true));
        verifyNoInteractions(messages, retriever, ai);
    }

    @Test
    void historyRestoresPersistedModeAndCitations() {
        existing(7L, 3L);
        ChatMessage message = new ChatMessage(); message.setRole("ASSISTANT"); message.setContent("answer");
        message.setMode("OPENAI_COMPATIBLE");
        message.setCitationsJson("[{\"index\":1,\"documentId\":9,\"documentName\":\"manual.pdf\",\"pageNumber\":3,\"chunkId\":6,\"excerpt\":\"source\"}]");
        when(messages.selectList(any())).thenReturn(List.of(message));
        ChatMessageVO restored = service.listMessages(2L, user).getFirst();
        assertEquals("OPENAI_COMPATIBLE", restored.mode());
        assertEquals(9L, restored.citations().getFirst().documentId());
        verify(permissions).requireKnowledgeBaseAccess(3L, user, false);
    }

    @Test
    void deletedSessionRemovesMessagesAndRejectsForeignOwner() {
        existing(7L, 3L);
        service.deleteSession(2L, user);
        verify(messages).delete(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));
        verify(sessions).deleteById(2L);
        existing(8L, 3L);
        assertThrows(BusinessException.class, () -> service.deleteSession(2L, user));
        verify(sessions, times(1)).deleteById(2L);
    }

    @Test
    void cancellationDuringGenerationDoesNotPersistSuccessfulAssistant() {
        existing(7L, 3L);
        DocumentChunk chunk = new DocumentChunk(); chunk.setId(6L); chunk.setKnowledgeBaseId(3L);
        when(retriever.retrieve(3L, "question", 5)).thenReturn(new RetrievalResult(List.of(new RetrievedChunk(chunk, 1, 1, 1)), true));
        AtomicBoolean cancelled = new AtomicBoolean();
        when(ai.stream(any(), any(), any())).thenAnswer(invocation -> { cancelled.set(true); return new AiResponse("partial", "OPENAI_COMPATIBLE"); });
        assertThrows(CancellationException.class, () -> service.answerStream(3L, "question", user, 2L, ignored -> {}, cancelled::get));
        ArgumentCaptor<ChatMessage> capture = ArgumentCaptor.forClass(ChatMessage.class);
        verify(messages).insert(capture.capture());
        assertEquals("USER", capture.getValue().getRole());
    }

    @Test
    void providerReceivesUserAndAssistantHistoryInChronologicalOrder() {
        existing(7L, 3L);
        ChatMessage assistant = new ChatMessage(); assistant.setRole("ASSISTANT"); assistant.setContent("Prior answer");
        ChatMessage question = new ChatMessage(); question.setRole("USER"); question.setContent("Original subject");
        when(messages.selectList(any())).thenReturn(List.of(assistant, question));
        DocumentChunk chunk = new DocumentChunk(); chunk.setId(6L); chunk.setKnowledgeBaseId(3L);
        when(retriever.retrieve(3L, "Original subject why?", 5)).thenReturn(new RetrievalResult(List.of(new RetrievedChunk(chunk, 1, 1, 1)), true));
        when(ai.stream(any(), any(), any())).thenReturn(new AiResponse("answer", "OPENAI_COMPATIBLE"));
        when(citations.build(chunk, 1)).thenReturn(new Citation(1, 9L, "manual.pdf", 3, 6L, "source"));
        service.answer(3L, "why?", user, 2L);
        ArgumentCaptor<AiRequest> request = ArgumentCaptor.forClass(AiRequest.class);
        verify(ai).stream(request.capture(), any(), any());
        assertEquals(List.of(new ConversationMessage("user", "Original subject"), new ConversationMessage("assistant", "Prior answer")), request.getValue().history());
        ArgumentCaptor<ChatMessage> capture = ArgumentCaptor.forClass(ChatMessage.class);
        verify(messages, times(2)).insert(capture.capture());
        assertEquals("OPENAI_COMPATIBLE", capture.getAllValues().getLast().getMode());
        assertTrue(capture.getAllValues().getLast().getCitationsJson().contains("manual.pdf"));
    }

    private void existing(Long owner, Long knowledgeBaseId) {
        ChatSession session = new ChatSession(); session.setId(2L); session.setUserId(owner); session.setKnowledgeBaseId(knowledgeBaseId);
        when(sessions.selectById(2L)).thenReturn(session);
    }
}
