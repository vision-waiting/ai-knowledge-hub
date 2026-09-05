package com.example.knowledge.chat;

import com.example.knowledge.controller.ChatController;
import com.example.knowledge.rag.CitationBuilder;
import com.example.knowledge.rag.HybridRetriever;
import com.example.knowledge.mapper.ChatMessageMapper;
import com.example.knowledge.mapper.ChatSessionMapper;
import com.example.knowledge.rag.AiService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

class ChatControllerSseTest {
    @Test
    void streamReturnsSseEmitter() {
        ChatService service = new ChatService(mock(HybridRetriever.class), mock(AiService.class), mock(CitationBuilder.class), mock(ChatSessionMapper.class), mock(ChatMessageMapper.class), new QueryRewriteService());
        assertNotNull(new ChatController(service).stream(1L, 3L, "问题", null));
    }
}
