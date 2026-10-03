package com.example.knowledge.chat;

import com.example.knowledge.entity.*;
import com.example.knowledge.mapper.*;
import com.example.knowledge.security.JwtTokenProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ChatFlowIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired SysUserMapper users;
    @Autowired KnowledgeBaseMapper knowledgeBases;
    @Autowired DocumentMapper documents;
    @Autowired DocumentChunkMapper chunks;
    @Autowired JwtTokenProvider tokens;
    @Autowired ObjectMapper json;

    @Test
    void streamsPersistsContinuesAndDeletesOwnedSessionWithCitations() throws Exception {
        SysUser user = new SysUser(); user.setUsername("chat-" + UUID.randomUUID()); user.setPassword("test");
        user.setNickname("Chat test"); user.setStatus(1); user.setRole("USER"); users.insert(user);
        String bearer = "Bearer " + tokens.createToken(user);
        KnowledgeBase knowledge = new KnowledgeBase(); knowledge.setName("Chat test"); knowledge.setOwnerId(user.getId()); knowledge.setStatus(1); knowledgeBases.insert(knowledge);
        Document document = new Document(); document.setKnowledgeBaseId(knowledge.getId()); document.setFileName("redis.txt");
        document.setFileType("txt"); document.setFilePath("test.txt"); document.setStatus("SUCCESS"); documents.insert(document);
        DocumentChunk chunk = new DocumentChunk(); chunk.setKnowledgeBaseId(knowledge.getId()); chunk.setDocumentId(document.getId());
        chunk.setContent("Redis persistence stores snapshots and append only logs."); chunk.setChunkIndex(0); chunk.setPageNumber(1); chunks.insert(chunk);

        String first = stream(bearer, knowledge.getId(), "Redis persistence", null);
        assertTrue(first.contains("event:message"));
        assertEquals(1, countOccurrences(first, "event:meta"));
        assertTrue(first.contains("event:citation"));
        assertTrue(first.contains("LOCAL_DEMO"));
        var sessions = json.readTree(mvc.perform(get("/api/chat/sessions").header("Authorization", bearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("data");
        long sessionId = sessions.get(0).path("id").asLong();
        mvc.perform(get("/api/chat/sessions/{id}/messages", sessionId).header("Authorization", bearer))
                .andExpect(jsonPath("$.data[1].mode").value("LOCAL_DEMO"))
                .andExpect(jsonPath("$.data[1].citations[0].documentId").value(document.getId()));
        assertTrue(stream(bearer, knowledge.getId(), "why?", sessionId).contains("event:done"));
        mvc.perform(get("/api/chat/sessions/{id}/messages", sessionId).header("Authorization", bearer))
                .andExpect(jsonPath("$.data.length()").value(4));
        String refusal = stream(bearer, knowledge.getId(), "Weather forecast tomorrow", sessionId);
        assertTrue(refusal.contains("INSUFFICIENT_CONTEXT"));
        assertFalse(refusal.contains("event:citation"));
        mvc.perform(delete("/api/chat/sessions/{id}", sessionId).header("Authorization", bearer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
        mvc.perform(get("/api/chat/sessions/{id}/messages", sessionId).header("Authorization", bearer))
                .andExpect(status().isNotFound());
    }

    private String stream(String bearer, Long knowledgeBaseId, String question, Long sessionId) throws Exception {
        var request = get("/api/chat/stream").header("Authorization", bearer)
                .param("knowledgeBaseId", knowledgeBaseId.toString()).param("question", question);
        if (sessionId != null) request.param("sessionId", sessionId.toString());
        var result = mvc.perform(request).andExpect(request().asyncStarted()).andReturn();
        result.getAsyncResult(10000);
        return mvc.perform(asyncDispatch(result)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private static int countOccurrences(String value, String needle) {
        int count = 0;
        for (int offset = 0; (offset = value.indexOf(needle, offset)) >= 0; offset += needle.length()) count++;
        return count;
    }
}
