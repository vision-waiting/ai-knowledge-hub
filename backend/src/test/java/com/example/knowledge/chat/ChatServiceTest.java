package com.example.knowledge.chat;

import com.example.knowledge.mapper.ChatMessageMapper;
import com.example.knowledge.mapper.ChatSessionMapper;
import com.example.knowledge.rag.AiService;
import com.example.knowledge.rag.HybridRetriever;
import com.example.knowledge.rag.RetrievalResult;
import com.example.knowledge.rag.CitationBuilder;
import com.example.knowledge.security.JwtPrincipal;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class ChatServiceTest {
    @Test
    void insufficientContextDoesNotCallAiService() {
        HybridRetriever retriever = mock(HybridRetriever.class);
        when(retriever.retrieve(3L, "unknown", 5)).thenReturn(new RetrievalResult(List.of(), false));
        AiService aiService = mock(AiService.class);
        ChatService service = new ChatService(retriever, aiService, mock(CitationBuilder.class), mock(ChatSessionMapper.class), mock(ChatMessageMapper.class), new QueryRewriteService());

        ChatAnswer answer = service.answer(3L, "unknown", new JwtPrincipal(7L, "student", "USER"), null);

        assertTrue(answer.answer().contains("没有找到足够相关"));
        verifyNoInteractions(aiService);
    }
}

