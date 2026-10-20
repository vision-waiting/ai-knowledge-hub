package com.example.knowledge;

import com.example.knowledge.entity.KnowledgeBase;
import com.example.knowledge.entity.SysUser;
import com.example.knowledge.mapper.KnowledgeBaseMapper;
import com.example.knowledge.mapper.SysUserMapper;
import com.example.knowledge.security.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class KnowledgeVisibilityIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired SysUserMapper users;
    @Autowired KnowledgeBaseMapper bases;
    @Autowired JwtTokenProvider tokens;
    @Autowired com.example.knowledge.mapper.DocumentMapper documents;

    @Test
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void committedKnowledgeDeletionReturnsSuccessEvenWithAnOldStoragePath(@org.junit.jupiter.api.io.TempDir java.nio.file.Path temp) throws Exception {
        SysUser owner = user("USER");
        KnowledgeBase library = base(owner);
        var oldFile = java.nio.file.Files.writeString(temp.resolve("legacy.txt"), "outside configured uploads");
        var doc = new com.example.knowledge.entity.Document();
        doc.setKnowledgeBaseId(library.getId()); doc.setFileName("legacy.txt"); doc.setFileType("txt");
        doc.setFileSize(1L); doc.setFilePath(oldFile.toString()); doc.setStatus("SUCCESS");
        documents.insert(doc);
        try {
            mvc.perform(delete("/api/knowledge-bases/" + library.getId())
                .header("Authorization", "Bearer " + tokens.createToken(owner)))
                .andExpect(status().isOk());
            org.junit.jupiter.api.Assertions.assertNull(bases.selectById(library.getId()));
            org.junit.jupiter.api.Assertions.assertNull(documents.selectById(doc.getId()));
            org.junit.jupiter.api.Assertions.assertTrue(java.nio.file.Files.exists(oldFile));
        } finally {
            documents.deleteById(doc.getId()); bases.deleteById(library.getId()); users.deleteById(owner.getId());
        }
    }

    @Test void usersCanPublishAndWithdrawWhileOnlyOwnerAndAdminCanDelete() throws Exception {
        SysUser owner = user("USER"), reader = user("USER"), admin = user("ADMIN");
        String ownerAuth = "Bearer " + tokens.createToken(owner), readerAuth = "Bearer " + tokens.createToken(reader);
        var response = mvc.perform(post("/api/knowledge-bases").header("Authorization", ownerAuth)
            .contentType("application/json").content("{\"name\":\"published-test\",\"shared\":true}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.shared").value(true)).andReturn();
        long id = new com.fasterxml.jackson.databind.ObjectMapper().readTree(response.getResponse().getContentAsString()).path("data").path("id").asLong();
        mvc.perform(get("/api/knowledge-bases").param("keyword", "published-test").header("Authorization", readerAuth))
            .andExpect(jsonPath("$.data", hasSize(1))).andExpect(jsonPath("$.data[0].ownerUsername").value(owner.getUsername()));
        mvc.perform(delete("/api/knowledge-bases/" + id).header("Authorization", readerAuth)).andExpect(status().isForbidden());
        mvc.perform(put("/api/knowledge-bases/" + id).header("Authorization", readerAuth)
            .contentType("application/json").content("{\"name\":\"changed\",\"shared\":false}")).andExpect(status().isForbidden());
        mvc.perform(put("/api/knowledge-bases/" + id).header("Authorization", ownerAuth)
            .contentType("application/json").content("{\"name\":\"published-test\",\"shared\":false}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.shared").value(false));
        mvc.perform(get("/api/knowledge-bases/" + id).header("Authorization", readerAuth)).andExpect(status().isForbidden());
        mvc.perform(delete("/api/knowledge-bases/" + id).header("Authorization", ownerAuth)).andExpect(status().isOk());
        KnowledgeBase another = base(owner);
        mvc.perform(delete("/api/knowledge-bases/" + another.getId()).header("Authorization", "Bearer " + tokens.createToken(admin))).andExpect(status().isOk());
    }

    @Test void administratorsCanChoosePrivateLibraries() throws Exception {
        String auth = "Bearer " + tokens.createToken(user("ADMIN"));
        var response = mvc.perform(post("/api/knowledge-bases").header("Authorization", auth)
            .contentType("application/json").content("{\"name\":\"admin-public\",\"shared\":false}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.shared").value(false)).andReturn();
        long id = new com.fasterxml.jackson.databind.ObjectMapper().readTree(response.getResponse().getContentAsString()).path("data").path("id").asLong();
        mvc.perform(put("/api/knowledge-bases/" + id).header("Authorization", auth)
            .contentType("application/json").content("{\"name\":\"admin-public\",\"shared\":false}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.shared").value(false));
    }

    @Test void sharedDocumentsAreReadableButCannotBeChangedByReaders() throws Exception {
        KnowledgeBase shared = base(user("ADMIN"));
        String auth = "Bearer " + tokens.createToken(user("USER"));
        var document = new com.example.knowledge.entity.Document();
        document.setKnowledgeBaseId(shared.getId()); document.setFileName("shared.txt");
        document.setFileType("txt"); document.setFileSize(1L); document.setFilePath("shared.txt"); document.setStatus("SUCCESS");
        documents.insert(document);
        mvc.perform(get("/api/documents/" + document.getId()).header("Authorization", auth))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.fileName").value("shared.txt"));
        mvc.perform(get("/api/documents/" + document.getId() + "/chunks").header("Authorization", auth)).andExpect(status().isOk());
        mvc.perform(multipart("/api/knowledge-bases/" + shared.getId() + "/documents")
            .file(new org.springframework.mock.web.MockMultipartFile("file", "test.txt", "text/plain", new byte[]{1}))
            .header("Authorization", auth)).andExpect(status().isForbidden());
        mvc.perform(delete("/api/documents/" + document.getId()).header("Authorization", auth)).andExpect(status().isForbidden());
        mvc.perform(post("/api/documents/" + document.getId() + "/reindex").header("Authorization", auth)).andExpect(status().isForbidden());
    }

    @Test void visibilityAndWritePermissionsFollowCreator() throws Exception {
        SysUser admin = user("ADMIN"), owner = user("USER"), other = user("USER");
        KnowledgeBase shared = base(admin), own = base(owner), privateBase = base(other);
        String auth = "Bearer " + tokens.createToken(owner);
        mvc.perform(get("/api/knowledge-bases").param("keyword", "visibility-test").header("Authorization", auth))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data", hasSize(2)))
            .andExpect(jsonPath("$.data[*].id", containsInAnyOrder(shared.getId().intValue(), own.getId().intValue())));
        mvc.perform(get("/api/knowledge-bases/" + shared.getId()).header("Authorization", auth)).andExpect(status().isOk());
        mvc.perform(get("/api/knowledge-bases/" + privateBase.getId()).header("Authorization", auth)).andExpect(status().isForbidden());
        mvc.perform(get("/api/knowledge-bases/" + shared.getId() + "/documents").header("Authorization", auth)).andExpect(status().isOk());
        mvc.perform(get("/api/knowledge-bases/" + privateBase.getId() + "/documents").header("Authorization", auth)).andExpect(status().isForbidden());
        mvc.perform(delete("/api/knowledge-bases/" + shared.getId()).header("Authorization", auth)).andExpect(status().isForbidden());
        mvc.perform(put("/api/knowledge-bases/" + shared.getId()).header("Authorization", auth)
            .contentType("application/json").content("{\"name\":\"changed\"}")).andExpect(status().isForbidden());
        mvc.perform(get("/api/knowledge-bases").param("keyword", "visibility-test").header("Authorization", "Bearer " + tokens.createToken(admin)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data", hasSize(3)))
            .andExpect(jsonPath("$.data[*].ownerUsername", containsInAnyOrder(admin.getUsername(), owner.getUsername(), other.getUsername())));
    }
    private SysUser user(String role) {
        SysUser u = new SysUser(); u.setUsername(java.util.UUID.randomUUID().toString()); u.setPassword("unused"); u.setRole(role); u.setStatus(1); users.insert(u); return u;
    }
    private KnowledgeBase base(SysUser owner) {
        KnowledgeBase b = new KnowledgeBase(); b.setName("visibility-test"); b.setOwnerId(owner.getId()); b.setStatus(1); b.setShared("ADMIN".equals(owner.getRole())); b.setDocumentCount(0); bases.insert(b); return b;
    }
}
