package com.example.knowledge.chat;

import com.example.knowledge.entity.SysUser;
import com.example.knowledge.mapper.SysUserMapper;
import com.example.knowledge.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import java.util.UUID;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
class FeedbackIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired SysUserMapper users;
    @Autowired JwtTokenProvider tokens;
    @Autowired ChatService chat;
    @Autowired com.fasterxml.jackson.databind.ObjectMapper json;
    String owner, reader, stranger;
    long kb, session, answer, question;
    private SysUser user() {
        SysUser u = new SysUser(); u.setUsername(UUID.randomUUID().toString()); u.setPassword("unused"); u.setRole("USER"); u.setStatus(1); users.insert(u); return u;
    }
    @BeforeEach void seed() {
        var a = user(); var b = user(); var c = user();
        owner = "Bearer " + tokens.createToken(a); reader = "Bearer " + tokens.createToken(b); stranger = "Bearer " + tokens.createToken(c);
        jdbc.update("INSERT INTO knowledge_base(name,owner_id,shared) VALUES ('feedback',?,TRUE)", a.getId());
        kb = jdbc.queryForObject("SELECT id FROM knowledge_base WHERE owner_id=?", Long.class,a.getId());
        jdbc.update("INSERT INTO chat_session(user_id,knowledge_base_id,title) VALUES (?,?,'question')", b.getId(),kb);
        session = jdbc.queryForObject("SELECT id FROM chat_session WHERE user_id=?",Long.class,b.getId());
        jdbc.update("INSERT INTO chat_message(session_id,role,content) VALUES (?,'USER','Redis 如何持久化？')",session);
        question = jdbc.queryForObject("SELECT id FROM chat_message WHERE session_id=?",Long.class,session);
        jdbc.update("INSERT INTO chat_message(session_id,role,content) VALUES (?,'ASSISTANT','回答内容')",session);
        answer = jdbc.queryForObject("SELECT id FROM chat_message WHERE session_id=? AND role='ASSISTANT'",Long.class,session);
    }
    @Test void feedbackPersistsAndManagersCanResolveOnlySubmittedAnswer() throws Exception {
        mvc.perform(put("/api/chat/messages/"+answer+"/feedback").header("Authorization",reader).contentType(MediaType.APPLICATION_JSON)
                .content("{\"feedback\":\"NOT_HELPFUL\",\"reason\":\"资料过时\"}")).andExpect(status().isOk());
        mvc.perform(get("/api/chat/sessions/"+session+"/messages").header("Authorization",reader))
                .andExpect(jsonPath("$.data[1].feedback").value("NOT_HELPFUL")).andExpect(jsonPath("$.data[1].feedbackReason").value("资料过时"));
        mvc.perform(get("/api/feedback/issues").param("knowledgeBaseId", ""+kb).header("Authorization",owner))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].messageId").value(answer))
                .andExpect(jsonPath("$.data[0].question").value("Redis 如何持久化？")).andExpect(jsonPath("$.data[0].status").value("OPEN"));
        mvc.perform(put("/api/feedback/issues/"+answer).header("Authorization",owner).contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"RESOLVED\"}")).andExpect(status().isOk());
        mvc.perform(get("/api/feedback/issues").param("knowledgeBaseId", ""+kb).param("status","RESOLVED").header("Authorization",owner))
                .andExpect(jsonPath("$.data[0].status").value("RESOLVED"));
    }
    @Test void feedbackCannotEditOtherUsersAnswersOrReadOthersPrivateConversation() throws Exception {
        mvc.perform(put("/api/chat/messages/"+answer+"/feedback").header("Authorization",owner).contentType(MediaType.APPLICATION_JSON)
                .content("{\"feedback\":\"HELPFUL\"}")).andExpect(status().isForbidden());
        mvc.perform(get("/api/feedback/issues").param("knowledgeBaseId", ""+kb).header("Authorization",stranger)).andExpect(status().isForbidden());
        mvc.perform(put("/api/chat/messages/"+question+"/feedback").header("Authorization",reader).contentType(MediaType.APPLICATION_JSON)
                .content("{\"feedback\":\"HELPFUL\"}")).andExpect(status().isNotFound());
        mvc.perform(get("/api/chat/sessions/"+session+"/messages").header("Authorization",owner)).andExpect(status().isForbidden());
    }
    @Test void validatesFeedbackAndResolutionValues() throws Exception {
        mvc.perform(put("/api/chat/messages/"+answer+"/feedback").header("Authorization",reader).contentType(MediaType.APPLICATION_JSON)
                .content("{\"feedback\":\"INVALID\"}")).andExpect(status().isBadRequest());
        mvc.perform(put("/api/feedback/issues/"+answer).header("Authorization",owner).contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"INVALID\"}")).andExpect(status().isBadRequest());
    }
    @Test void streamedMetadataContainsSavedAnswerId() {
        Long readerId = jdbc.queryForObject("SELECT user_id FROM chat_session WHERE id=?", Long.class, session);
        java.util.concurrent.atomic.AtomicReference<ChatMetadata> metadata = new java.util.concurrent.atomic.AtomicReference<>();
        chat.answerStream(kb, "你好", new com.example.knowledge.security.JwtPrincipal(readerId,"reader","USER"), session,
                fragment -> {}, () -> false, metadata::set);
        long id = json.valueToTree(metadata.get()).path("messageId").asLong();
        org.junit.jupiter.api.Assertions.assertTrue(id > 0, "new answers must be immediately eligible for feedback");
        org.junit.jupiter.api.Assertions.assertEquals("ASSISTANT", jdbc.queryForObject("SELECT role FROM chat_message WHERE id=?", String.class, id));
    }
}
