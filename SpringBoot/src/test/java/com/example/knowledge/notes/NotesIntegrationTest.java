package com.example.knowledge.notes;

import com.example.knowledge.entity.SysUser;
import com.example.knowledge.mapper.SysUserMapper;
import com.example.knowledge.security.JwtTokenProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import java.util.Map;
import java.util.UUID;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NotesIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired SysUserMapper users;
    @Autowired JwtTokenProvider tokens;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    String ownerToken;
    String otherToken;
    Long ownerId;
    Long otherId;

    @BeforeEach void seed() {
        SysUser owner = user("USER"); SysUser other = user("ADMIN");
        ownerId = owner.getId(); otherId = other.getId();
        ownerToken = "Bearer " + tokens.createToken(owner); otherToken = "Bearer " + tokens.createToken(other);
    }
    private SysUser user(String role) {
        SysUser user = new SysUser(); user.setUsername(UUID.randomUUID().toString());
        user.setPassword("unused"); user.setRole(role); user.setStatus(1); users.insert(user); return user;
    }
    private long create(Map<String, Object> body) throws Exception {
        String response = mvc.perform(post("/api/notes").header("Authorization", ownerToken)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.updatedAt").isString())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(response).path("data").path("id").asLong();
    }
    @Test void privateCrudIncludesSearchAndCategories() throws Exception {
        long id = create(Map.of("title", "Original", "content", "Concurrency **notes**", "category", "Java"));
        create(Map.of("title", "Other", "content", "Frontend", "category", "Web"));
        mvc.perform(get("/api/notes").header("Authorization", ownerToken).param("keyword", "Concurrency").param("category", "Java"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(1)).andExpect(jsonPath("$.data[0].id").value(id));
        mvc.perform(get("/api/notes/categories").header("Authorization", ownerToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(2));
        mvc.perform(put("/api/notes/" + id).header("Authorization", ownerToken).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Changed\",\"content\":\"New content\",\"category\":\"Review\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.title").value("Changed"));
        mvc.perform(get("/api/notes/" + id).header("Authorization", ownerToken)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").value("New content"));
        mvc.perform(delete("/api/notes/" + id).header("Authorization", ownerToken)).andExpect(status().isOk());
        mvc.perform(get("/api/notes/" + id).header("Authorization", ownerToken)).andExpect(status().isNotFound());
    }
    @Test void evenAdminCannotReadModifyOrDeleteAnotherUsersNote() throws Exception {
        long id = create(Map.of("title", "Private", "content", "Secret"));
        mvc.perform(get("/api/notes").header("Authorization", otherToken)).andExpect(status().isOk()).andExpect(jsonPath("$.data").isEmpty());
        mvc.perform(get("/api/notes/" + id).header("Authorization", otherToken)).andExpect(status().isNotFound());
        mvc.perform(put("/api/notes/" + id).header("Authorization", otherToken).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Changed\",\"content\":\"Changed\"}")).andExpect(status().isNotFound());
        mvc.perform(delete("/api/notes/" + id).header("Authorization", otherToken)).andExpect(status().isNotFound());
    }
    @Test void validatesFieldsAndOwnedSourceSession() throws Exception {
        mvc.perform(post("/api/notes").header("Authorization", ownerToken).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\" \",\"content\":\"text\"}")).andExpect(status().isBadRequest());
        jdbc.update("INSERT INTO chat_session (user_id, knowledge_base_id, title) VALUES (?, 0, 'notes source')", otherId);
        Long foreignSession = jdbc.queryForObject("SELECT id FROM chat_session WHERE user_id = ?", Long.class, otherId);
        mvc.perform(post("/api/notes").header("Authorization", ownerToken).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("title", "Source", "content", "text", "sourceSessionId", foreignSession))))
                .andExpect(status().isNotFound());
        jdbc.update("INSERT INTO chat_session (user_id, knowledge_base_id, title) VALUES (?, 0, 'own source')", ownerId);
        Long ownSession = jdbc.queryForObject("SELECT id FROM chat_session WHERE user_id = ?", Long.class, ownerId);
        long id = create(Map.of("title", "Source", "content", "text", "sourceSessionId", ownSession));
        jdbc.update("DELETE FROM chat_session WHERE id = ?", ownSession);
        mvc.perform(put("/api/notes/" + id).header("Authorization", ownerToken).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("title", "Edited", "content", "kept after deletion", "sourceSessionId", ownSession))))
                .andExpect(status().isOk());
        mvc.perform(get("/api/notes")).andExpect(status().isUnauthorized());
    }
}
