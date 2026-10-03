package com.example.knowledge.document;

import com.example.knowledge.entity.Document;
import com.example.knowledge.entity.SysUser;
import com.example.knowledge.mapper.DocumentMapper;
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
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
class DocumentVersionsIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired SysUserMapper users;
    @Autowired DocumentMapper documents;
    @Autowired JwtTokenProvider tokens;
    @Autowired JdbcTemplate jdbc;
    @Autowired DocumentVersionActivation activation;
    @Autowired com.example.knowledge.service.StatisticsQueryService statistics;
    private SysUser user() { SysUser u = new SysUser(); u.setUsername(UUID.randomUUID().toString()); u.setPassword("unused"); u.setRole("USER"); u.setStatus(1); users.insert(u); return u; }
    private Long base(SysUser owner) {
        jdbc.update("INSERT INTO knowledge_base(name,owner_id,document_count) VALUES ('versions',?,1)",owner.getId());
        return jdbc.queryForObject("SELECT id FROM knowledge_base WHERE owner_id=?",Long.class,owner.getId());
    }
    private Document document(Long base, String status, Long previous) {
        Document d = new Document(); d.setKnowledgeBaseId(base); d.setFileName(UUID.randomUUID()+".txt"); d.setFilePath("unused");
        d.setFileType("txt"); d.setStatus(status); d.setVersionNumber(previous==null?1:2); d.setVersionRootId(previous); d.setPreviousVersionId(previous);
        documents.insert(d); return d;
    }
    @Test void deletingBaseRemovesMaintenanceDataWithoutTouchingOtherBases() throws Exception {
        SysUser owner=user(); Long base=base(owner), retained=base(user());
        for(Long id:java.util.List.of(base,retained)) {
            jdbc.update("INSERT INTO knowledge_member(knowledge_base_id,user_id,role) VALUES (?,?,'VIEWER')",id,owner.getId());
            jdbc.update("INSERT INTO knowledge_content(knowledge_base_id,kind,title,body) VALUES (?,'FAQ','question','answer')",id);
            jdbc.update("INSERT INTO web_source(knowledge_base_id,url,url_hash,title,document_id,content_hash) VALUES (?,'https://example.com','hash','title',1,'hash')",id);
            jdbc.update("INSERT INTO review_card(user_id,study_id,knowledge_base_id,card_index,front,back) VALUES (?,?,?,0,'front','back')",owner.getId(),id,id);
        }
        mvc.perform(delete("/api/knowledge-bases/"+base).header("Authorization","Bearer "+tokens.createToken(owner))).andExpect(status().isOk());
        for(String table:java.util.List.of("knowledge_member","knowledge_content","web_source","review_card")) {
            assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM "+table+" WHERE knowledge_base_id=?",Integer.class,base));
            assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM "+table+" WHERE knowledge_base_id=?",Integer.class,retained));
        }
    }

    @Test void activationPreservesOldChunksAndOnlyPublishesOneCurrentVersion() {
        SysUser owner=user(); Long base=base(owner); Document old=document(base,"SUCCESS",null);
        jdbc.update("INSERT INTO document_chunk(document_id,knowledge_base_id,content,chunk_index) VALUES (?,?,'historical source',0)",old.getId(),base);
        Document next=document(base,"INDEXING",old.getId());
        activation.activate(next);
        assertEquals("ARCHIVED",documents.selectById(old.getId()).getStatus());
        assertEquals("SUCCESS",documents.selectById(next.getId()).getStatus());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM document WHERE knowledge_base_id=? AND status='SUCCESS'",Integer.class,base));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM document_chunk WHERE document_id=?",Integer.class,old.getId()));
        assertEquals(1,statistics.overview(new com.example.knowledge.security.JwtPrincipal(owner.getId(),owner.getUsername(),"USER")).documentCount());
    }
    @Test void trashRetainsCitationsAndRestoreRequiresOwnerAndRejectsCurrentConflict() throws Exception {
        SysUser owner=user(), stranger=user(); Long base=base(owner); Document doc=document(base,"SUCCESS",null);
        String auth="Bearer "+tokens.createToken(owner), other="Bearer "+tokens.createToken(stranger);
        jdbc.update("INSERT INTO document_chunk(document_id,knowledge_base_id,content,chunk_index) VALUES (?,?,'stable citation',0)",doc.getId(),base);
        mvc.perform(delete("/api/documents/"+doc.getId()).header("Authorization",auth)).andExpect(status().isOk());
        mvc.perform(get("/api/knowledge-bases/"+base+"/documents").header("Authorization",auth)).andExpect(jsonPath("$.data").isEmpty());
        mvc.perform(get("/api/documents/"+doc.getId()+"/chunks").header("Authorization",auth)).andExpect(jsonPath("$.data[0].content").value("stable citation"));
        mvc.perform(post("/api/documents/"+doc.getId()+"/restore").header("Authorization",other)).andExpect(status().isForbidden());
        mvc.perform(get("/api/documents/"+doc.getId()+"/versions").header("Authorization",other)).andExpect(status().isForbidden());
        mvc.perform(post("/api/documents/"+doc.getId()+"/restore").header("Authorization",auth)).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("SUCCESS"));
        mvc.perform(delete("/api/documents/"+doc.getId()).header("Authorization",auth)).andExpect(status().isOk());
        document(base,"SUCCESS",doc.getId());
        mvc.perform(post("/api/documents/"+doc.getId()+"/restore").header("Authorization",auth)).andExpect(status().isConflict());
        assertEquals("TRASHED",documents.selectById(doc.getId()).getStatus());
    }
}
