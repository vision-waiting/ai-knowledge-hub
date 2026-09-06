package com.example.knowledge.chat;

import com.example.knowledge.mapper.ChatMessageMapper;
import com.example.knowledge.mapper.ChatSessionMapper;
import com.example.knowledge.rag.AiService;
import com.example.knowledge.rag.HybridRetriever;
import com.example.knowledge.rag.RetrievalResult;
import com.example.knowledge.rag.CitationBuilder;
import com.example.knowledge.security.JwtPrincipal;
import com.example.knowledge.service.PermissionService;
import com.example.knowledge.common.BusinessException;
import com.example.knowledge.common.ErrorCode;
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
        PermissionService permissionService = mock(PermissionService.class);
        ChatService service = new ChatService(retriever, aiService, mock(CitationBuilder.class), mock(ChatSessionMapper.class), mock(ChatMessageMapper.class), new QueryRewriteService(), permissionService);

        ChatAnswer answer = service.answer(3L, "unknown", new JwtPrincipal(7L, "student", "USER"), null);

        assertTrue(answer.answer().contains("没有找到足够相关"));
        verifyNoInteractions(aiService);
    }

    @Test
    void forbiddenKnowledgeBaseDoesNotTouchRetrievalOrChatCollaborators() {
        HybridRetriever retriever = mock(HybridRetriever.class);
        AiService aiService = mock(AiService.class);
        CitationBuilder citationBuilder = mock(CitationBuilder.class);
        ChatSessionMapper sessionMapper = mock(ChatSessionMapper.class);
        ChatMessageMapper messageMapper = mock(ChatMessageMapper.class);
        PermissionService permissionService = mock(PermissionService.class);
        ChatService service = new ChatService(retriever, aiService, citationBuilder, sessionMapper, messageMapper,
                new QueryRewriteService(), permissionService);

        JwtPrincipal user = new JwtPrincipal(7L, "student", "USER");
        doThrow(new BusinessException(ErrorCode.FORBIDDEN))
                .when(permissionService).requireKnowledgeBaseAccess(3L, user, false);

        org.junit.jupiter.api.Assertions.assertThrows(BusinessException.class,
                () -> service.answer(3L, "secret", user, null));
        verifyNoInteractions(retriever, aiService, citationBuilder, sessionMapper, messageMapper);
    }
}

