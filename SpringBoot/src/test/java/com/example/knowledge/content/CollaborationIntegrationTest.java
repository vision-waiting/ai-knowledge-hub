package com.example.knowledge.content;

import com.example.knowledge.entity.SysUser;
import com.example.knowledge.mapper.SysUserMapper;
import com.example.knowledge.security.JwtTokenProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
class CollaborationIntegrationTest {
    @Autowired MockMvc mvc; @Autowired JdbcTemplate jdbc; @Autowired SysUserMapper users;
    @Autowired JwtTokenProvider tokens; @Autowired ObjectMapper json;
    private String user(String role) {
        var u = new SysUser(); u.setUsername(UUID.randomUUID().toString()); u.setPassword("unused"); u.setRole(role); u.setStatus(1); users.insert(u);
        return "Bearer " + tokens.createToken(u);
    }
    private long create(String auth, String url, Object body) throws Exception {
        var result = mvc.perform(post(url).header("Authorization", auth).contentType("application/json").content(json.writeValueAsString(body)))
                .andExpect(status().isOk()).andReturn();
        return json.readTree(result.getResponse().getContentAsString()).path("data").path("id").asLong();
    }
    private long base(String auth) throws Exception { return create(auth,"/api/knowledge-bases",Map.of("name","collaboration","shared",true)); }
    private long entry(String auth, String root) throws Exception { return create(auth,root+"/content",Map.of("kind","FAQ","title","Original","body","Before")); }
    private Map<String,Object> proposal(long id, int version) { return Map.of("contentId",id,"baseVersion",version,"title","Improved","body","After","reason","Correct outdated information"); }

    @Test void readerProposesManagerMergesOnceAndHistoryCanBeRestored() throws Exception {
        String owner=user("USER"), reader=user("USER"), root="/api/knowledge-bases/"+base(owner);
        long content=entry(owner,root), suggestion=create(reader,root+"/suggestions",proposal(content,1));
        assertEquals("Before",jdbc.queryForObject("SELECT body FROM knowledge_content WHERE id=?",String.class,content));
        mvc.perform(get(root+"/suggestions").header("Authorization",owner)).andExpect(jsonPath("$.data.items[0].originalBody").value("Before"));
        mvc.perform(post(root+"/suggestions/"+suggestion+"/merge").header("Authorization",reader)).andExpect(status().isForbidden());
        mvc.perform(post(root+"/suggestions/"+suggestion+"/merge").header("Authorization",owner)).andExpect(status().isOk());
        mvc.perform(post(root+"/suggestions/"+suggestion+"/merge").header("Authorization",owner)).andExpect(status().isConflict());
        assertEquals("After",jdbc.queryForObject("SELECT body FROM knowledge_content WHERE id=?",String.class,content));
        mvc.perform(get(root+"/content/"+content+"/history").header("Authorization",reader))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].version").value(2)).andExpect(jsonPath("$.data[0].action").value("MERGED"));
        mvc.perform(post(root+"/content/"+content+"/history/1/restore").header("Authorization",reader)).andExpect(status().isForbidden());
        mvc.perform(post(root+"/content/"+content+"/history/1/restore").header("Authorization",owner)).andExpect(status().isOk());
        assertEquals("Before",jdbc.queryForObject("SELECT body FROM knowledge_content WHERE id=?",String.class,content));
        mvc.perform(get(root+"/content/"+content+"/history").header("Authorization",reader)).andExpect(jsonPath("$.data[0].version").value(3));
    }
    @Test void staleProposalsCannotOverwriteNewEditsAndCanBeClosed() throws Exception {
        String owner=user("USER"), reader=user("USER"), root="/api/knowledge-bases/"+base(owner);
        long content=entry(owner,root), suggestion=create(reader,root+"/suggestions",proposal(content,1));
        mvc.perform(put(root+"/content/"+content).header("Authorization",owner).contentType("application/json")
                .content("{\"kind\":\"FAQ\",\"title\":\"Latest\",\"body\":\"Keep latest\",\"baseVersion\":1}")).andExpect(status().isOk());
        mvc.perform(post(root+"/suggestions/"+suggestion+"/merge").header("Authorization",owner)).andExpect(status().isConflict());
        assertEquals("Keep latest",jdbc.queryForObject("SELECT body FROM knowledge_content WHERE id=?",String.class,content));
        mvc.perform(post(root+"/suggestions").header("Authorization",reader).contentType("application/json").content(json.writeValueAsString(proposal(content,1)))).andExpect(status().isConflict());
        mvc.perform(post(root+"/suggestions/"+suggestion+"/close").header("Authorization",owner)).andExpect(status().isOk());
        mvc.perform(get(root+"/suggestions").param("status","CLOSED").header("Authorization",reader)).andExpect(jsonPath("$.data.items[0].status").value("CLOSED"));
    }
    @Test void staleDirectEditsCannotOverwriteMergedSuggestions() throws Exception {
        String owner=user("USER"), reader=user("USER"), root="/api/knowledge-bases/"+base(owner);
        long content=entry(owner,root), suggestion=create(reader,root+"/suggestions",proposal(content,1));
        mvc.perform(post(root+"/suggestions/"+suggestion+"/merge").header("Authorization",owner)).andExpect(status().isOk());
        mvc.perform(put(root+"/content/"+content).header("Authorization",owner).contentType("application/json")
                .content(json.writeValueAsString(Map.of("kind","FAQ","title","Old draft","body","Before","baseVersion",1))))
                .andExpect(status().isConflict());
        assertEquals("After",jdbc.queryForObject("SELECT body FROM knowledge_content WHERE id=?",String.class,content));
        assertEquals(2,jdbc.queryForObject("SELECT version FROM knowledge_content WHERE id=?",Integer.class,content));
        mvc.perform(put(root+"/content/"+content).header("Authorization",owner).contentType("application/json")
                .content(json.writeValueAsString(Map.of("kind","FAQ","title","Current draft","body","Reviewed","baseVersion",2))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.version").value(3));
    }
    @Test void directEditsRequireTheDisplayedVersion() throws Exception {
        String owner=user("USER"), root="/api/knowledge-bases/"+base(owner);
        long content=entry(owner,root);
        mvc.perform(put(root+"/content/"+content).header("Authorization",owner).contentType("application/json")
                .content("{\"kind\":\"FAQ\",\"title\":\"Old client\",\"body\":\"Overwrite\"}"))
                .andExpect(status().isBadRequest());
        assertEquals("Before",jdbc.queryForObject("SELECT body FROM knowledge_content WHERE id=?",String.class,content));
    }
    @Test void starsAreIdempotentAndRevokingAccessHidesAllCollaborationData() throws Exception {
        String owner=user("USER"), reader=user("USER"), root="/api/knowledge-bases/"+base(owner);
        long content=entry(owner,root); create(reader,root+"/suggestions",proposal(content,1));
        for(int i=0;i<2;i++) mvc.perform(put(root+"/star").header("Authorization",reader)).andExpect(status().isOk()).andExpect(jsonPath("$.data.starCount").value(1));
        mvc.perform(get(root).header("Authorization",reader)).andExpect(jsonPath("$.data.starred").value(true));
        mvc.perform(put(root).header("Authorization",owner).contentType("application/json").content("{\"name\":\"private\",\"shared\":false}")).andExpect(status().isOk());
        for(String suffix:new String[]{"/star","/suggestions","/content/"+content+"/history"})
            mvc.perform(get(root+suffix).header("Authorization",reader)).andExpect(status().isForbidden());
        mvc.perform(delete(root+"/star").header("Authorization",reader)).andExpect(status().isOk());
    }
    @Test void crossLibraryProposalsAndInvalidInputsAreRejected() throws Exception {
        String owner=user("USER"), root="/api/knowledge-bases/"+base(owner), other="/api/knowledge-bases/"+base(owner);
        long content=entry(owner,root);
        mvc.perform(post(other+"/suggestions").header("Authorization",owner).contentType("application/json").content(json.writeValueAsString(proposal(content,1)))).andExpect(status().isNotFound());
        mvc.perform(post(root+"/suggestions").header("Authorization",owner).contentType("application/json").content("{}")).andExpect(status().isBadRequest());
        mvc.perform(get(root+"/suggestions").param("offset","-1").header("Authorization",owner)).andExpect(status().isBadRequest());
    }
    @Test void administratorCanKeepLibraryPrivate() throws Exception {
        String admin=user("ADMIN"), reader=user("USER");
        long id=create(admin,"/api/knowledge-bases",Map.of("name","Admin private","shared",false));
        mvc.perform(get("/api/knowledge-bases/"+id).header("Authorization",reader)).andExpect(status().isForbidden());
        mvc.perform(get("/api/knowledge-bases/"+id).header("Authorization",admin)).andExpect(jsonPath("$.data.shared").value(false));
    }
}
