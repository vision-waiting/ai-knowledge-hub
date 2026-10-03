package com.example.knowledge.search;

import com.example.knowledge.entity.SysUser;
import com.example.knowledge.mapper.SysUserMapper;
import com.example.knowledge.security.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import java.util.UUID;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
class SearchIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired SysUserMapper users;
    @Autowired JwtTokenProvider tokens;
    @Autowired JdbcTemplate jdbc;
    @Test void scopesLiteralBodySearchAndPaginatesFilteredChunks() throws Exception {
        SysUser owner = user(), stranger = user();
        String auth = "Bearer " + tokens.createToken(owner);
        jdbc.update("INSERT INTO knowledge_base(name,owner_id) VALUES ('search',?)", owner.getId());
        Long base = jdbc.queryForObject("SELECT id FROM knowledge_base WHERE owner_id=?", Long.class, owner.getId());
        jdbc.update("INSERT INTO document(knowledge_base_id,file_name,file_path,file_type,status) VALUES (?,'water.pdf','unused','pdf','SUCCESS')", base);
        Long doc = jdbc.queryForObject("SELECT id FROM document WHERE knowledge_base_id=?", Long.class, base);
        jdbc.update("INSERT INTO document_tag(document_id,tag_name) VALUES (?,'science')", doc);
        for (int i=0;i<3;i++) jdbc.update("INSERT INTO document_chunk(document_id,knowledge_base_id,content,chunk_index,page_number) VALUES (?,?,?, ?,1)", doc, base, i == 0 ? "水 100% literal_under" : "水 normal", i);
        mvc.perform(get("/api/search").header("Authorization", auth).param("knowledgeBaseId",base.toString()).param("q","水").param("limit","1").param("offset","1").param("fileName","water").param("fileType","pdf").param("tag","science"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(1)).andExpect(jsonPath("$.data[0].chunkIndex").value(1));
        for (String literal : new String[]{"%", "_"}) mvc.perform(get("/api/search").header("Authorization",auth).param("knowledgeBaseId",base.toString()).param("q",literal))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(1));
        mvc.perform(get("/api/search").header("Authorization",auth).param("knowledgeBaseId",base.toString()).param("q","水").param("tag","missing"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data").isEmpty());
        mvc.perform(get("/api/search").header("Authorization","Bearer " + tokens.createToken(stranger)).param("knowledgeBaseId",base.toString()).param("q","水"))
            .andExpect(status().isForbidden());
        mvc.perform(get("/api/search").header("Authorization",auth).param("knowledgeBaseId",base.toString()).param("q","水").param("limit","101"))
            .andExpect(status().isBadRequest());
    }
    private SysUser user() { SysUser user = new SysUser(); user.setUsername(UUID.randomUUID().toString()); user.setPassword("unused"); user.setRole("USER"); user.setStatus(1); users.insert(user); return user; }
    @Test void ranksDistinctTermsAndExcludesArchivedTrashedAndOtherBases() throws Exception {
        SysUser owner = user();
        jdbc.update("INSERT INTO knowledge_base(name,owner_id) VALUES ('rank',?)", owner.getId());
        Long base = jdbc.queryForObject("SELECT id FROM knowledge_base WHERE owner_id=?", Long.class, owner.getId());
        for (String state : new String[]{"SUCCESS", "ARCHIVED", "TRASHED", "FAILED"}) {
            jdbc.update("INSERT INTO document(knowledge_base_id,file_name,file_path,file_type,status) VALUES (?,?,'unused','txt',?)", base, state, state);
            Long doc = jdbc.queryForObject("SELECT id FROM document WHERE knowledge_base_id=? AND file_name=?", Long.class, base, state);
            jdbc.update("INSERT INTO document_chunk(document_id,knowledge_base_id,content,chunk_index) VALUES (?,?,'Redis 缓存',0)", doc, base);
            if (state.equals("SUCCESS")) jdbc.update("INSERT INTO document_chunk(document_id,knowledge_base_id,content,chunk_index) VALUES (?,?,'Redis only',1)", doc, base);
        }
        jdbc.update("INSERT INTO document(knowledge_base_id,file_name,file_path,status) VALUES (?,'foreign','unused','SUCCESS')", base+100000);
        Long foreign = jdbc.queryForObject("SELECT id FROM document WHERE knowledge_base_id=?", Long.class, base+100000);
        jdbc.update("INSERT INTO document_chunk(document_id,knowledge_base_id,content,chunk_index) VALUES (?,?,'Redis 缓存',0)", foreign, base+100000);
        mvc.perform(get("/api/search").header("Authorization", "Bearer " + tokens.createToken(owner)).param("knowledgeBaseId",base.toString()).param("q","Redis redis 缓存"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(2))
            .andExpect(jsonPath("$.data[0].score").value(1.0)).andExpect(jsonPath("$.data[1].score").value(0.5))
            .andExpect(jsonPath("$.data[0].documentName").value("SUCCESS"));
    }
}
