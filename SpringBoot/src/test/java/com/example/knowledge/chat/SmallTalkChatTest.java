package com.example.knowledge.chat;

import com.example.knowledge.entity.ChatMessage;
import com.example.knowledge.entity.ChatSession;
import com.example.knowledge.mapper.ChatMessageMapper;
import com.example.knowledge.mapper.ChatSessionMapper;
import com.example.knowledge.rag.*;
import com.example.knowledge.security.JwtPrincipal;
import com.example.knowledge.service.PermissionService;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SmallTalkChatTest {
    private final HybridRetriever retriever = mock(HybridRetriever.class);
    private final AiService ai = mock(AiService.class);
    private final CitationBuilder citations = mock(CitationBuilder.class);
    private final ChatSessionMapper sessions = mock(ChatSessionMapper.class);
    private final ChatMessageMapper messages = mock(ChatMessageMapper.class);
    private final PermissionService permissions = mock(PermissionService.class);
    private final JwtPrincipal user = new JwtPrincipal(7L, "student", "USER");
    private final ChatService service = new ChatService(retriever, ai, citations, sessions, messages,
            new QueryRewriteService(), permissions);

    private void existingSession() {
        ChatSession session = new ChatSession();
        session.setId(2L); session.setUserId(7L); session.setKnowledgeBaseId(3L);
        when(sessions.selectById(2L)).thenReturn(session);
        when(retriever.retrieve(anyLong(), anyString(), anyInt())).thenReturn(new RetrievalResult(List.of(), false));
    }

    @ParameterizedTest
    @ValueSource(strings = {"你好", "  你好！  ", "您好", "你好呀", "嗨", "Hello!", "HI", "早上好", "在吗？", "谢谢", "再见"})
    void smallTalkStreamsAndPersistsWithoutRetrieval(String question) {
        existingSession();
        StringBuilder streamed = new StringBuilder();
        ChatAnswer result = service.answerStream(3L, question, user, 2L, streamed::append, () -> false);

        assertEquals("SMALL_TALK", result.mode());
        assertFalse(result.answer().isBlank());
        assertEquals(result.answer(), streamed.toString());
        assertTrue(result.citations().isEmpty());
        verifyNoInteractions(retriever, ai, citations);
        verify(permissions).requireKnowledgeBaseAccess(3L, user, false);
        ArgumentCaptor<ChatMessage> saved = ArgumentCaptor.forClass(ChatMessage.class);
        verify(messages, times(2)).insert(saved.capture());
        assertEquals(question.trim(), saved.getAllValues().getFirst().getContent());
        assertEquals(result.answer(), saved.getAllValues().getLast().getContent());
        assertEquals("SMALL_TALK", saved.getAllValues().getLast().getMode());
        assertEquals("[]", saved.getAllValues().getLast().getCitationsJson());
    }

    @ParameterizedTest
    @ValueSource(strings = {"你好，请介绍这个项目", "谢谢，请总结文档", "你好是什么意思", "Hello world 项目如何启动", "文档的技术栈是什么"})
    void substantiveQuestionsStillRequireKnowledge(String question) {
        existingSession();
        ChatAnswer result = service.answer(3L, question, user, 2L);
        assertEquals("INSUFFICIENT_CONTEXT", result.mode());
        verify(retriever).retrieve(3L, question, 5);
    }
}
