package com.example.knowledge.chat;

import com.example.knowledge.entity.ChatSession;
import com.example.knowledge.mapper.ChatMessageMapper;
import com.example.knowledge.mapper.ChatSessionMapper;
import com.example.knowledge.rag.AiService;
import com.example.knowledge.rag.CitationBuilder;
import com.example.knowledge.rag.HybridRetriever;
import com.example.knowledge.security.JwtPrincipal;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChatHistoryServiceTest {
    @Test
    void listsOnlySessionsOwnedByCurrentUser() {
        ChatSession session = new ChatSession(); session.setId(2L); session.setUserId(7L); session.setKnowledgeBaseId(3L); session.setTitle("HashMap");
        ChatSessionMapper mapper = mock(ChatSessionMapper.class); when(mapper.selectList(any())).thenReturn(List.of(session));
        ChatService service = new ChatService(mock(HybridRetriever.class), mock(AiService.class), mock(CitationBuilder.class), mapper, mock(ChatMessageMapper.class), new QueryRewriteService());
        assertEquals(1, service.listSessions(new JwtPrincipal(7L, "student", "USER")).size());
    }
}

