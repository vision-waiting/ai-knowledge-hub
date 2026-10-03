package com.example.knowledge.insights;

import com.example.knowledge.chat.ChatService;
import com.example.knowledge.entity.SysUser;
import com.example.knowledge.mapper.SysUserMapper;
import com.example.knowledge.security.JwtPrincipal;
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
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
class InsightsIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired SysUserMapper users;
    @Autowired JwtTokenProvider tokens;
    @Autowired ChatService chat;
    SysUser owner, alice, bob;
    String ownerToken, aliceToken;
    long kb, privateKb;

    private SysUser user() {
        SysUser u = new SysUser(); u.setUsername(UUID.randomUUID().toString()); u.setPassword("unused"); u.setRole("USER"); u.setStatus(1); users.insert(u); return u;
    }
    private long base(SysUser owner, boolean shared) {
        String name = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO knowledge_base(name,owner_id,shared) VALUES (?,?,?)", name, owner.getId(), shared);
        return jdbc.queryForObject("SELECT id FROM knowledge_base WHERE name=?", Long.class, name);
    }
    private void unanswered(SysUser user, String question) {
        String title = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO chat_session(user_id,knowledge_base_id,title) VALUES (?,?,?)", user.getId(), kb, title);
        long session = jdbc.queryForObject("SELECT id FROM chat_session WHERE title=?", Long.class, title);
        jdbc.update("INSERT INTO chat_message(session_id,role,content) VALUES (?,'USER',?)", session, question);
        jdbc.update("INSERT INTO chat_message(session_id,role,content,mode) VALUES (?,'ASSISTANT','资料不足','INSUFFICIENT_CONTEXT')", session);
    }
    @BeforeEach void seed() {
        owner = user(); alice = user(); bob = user();
        ownerToken = "Bearer " + tokens.createToken(owner); aliceToken = "Bearer " + tokens.createToken(alice);
        kb = base(owner, true); privateKb = base(owner, false);
    }

    @Test void gapsOnlyRevealQuestionsAskedByAtLeastTwoUsers() throws Exception {
        unanswered(alice, "如何配置 SSO？"); unanswered(bob, "如何配置 sso"); unanswered(alice, "我的工资单在哪里？");
        mvc.perform(get("/api/knowledge-bases/" + kb + "/insights").header("Authorization", ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.insufficientCount").value(3))
                .andExpect(jsonPath("$.data.gaps.length()").value(1))
                .andExpect(jsonPath("$.data.gaps[0].askCount").value(2))
                .andExpect(jsonPath("$.data.gaps[0].userCount").value(2))
                .andExpect(jsonPath("$.data.hiddenGapCount").value(1))
                .andExpect(content().string(not(containsString("工资单"))));
        mvc.perform(get("/api/knowledge-bases/" + kb + "/insights").header("Authorization", aliceToken)).andExpect(status().isForbidden());
        mvc.perform(get("/api/knowledge-bases/" + kb + "/insights").param("days", "0").header("Authorization", ownerToken)).andExpect(status().isBadRequest());
    }

    @Test void evaluationRunsArePersistedForManagersOnly() throws Exception {
        String body = "{\"topK\":3,\"cases\":[{\"question\":\"知识库里没有的问题\",\"expectedDocumentIds\":[]}]}";
        mvc.perform(post("/api/knowledge-bases/" + kb + "/retrieval/evaluate").header("Authorization", ownerToken).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.id").isNumber()).andExpect(jsonPath("$.data.datasetHash").value(hasLength(12)));
        mvc.perform(post("/api/knowledge-bases/" + kb + "/retrieval/evaluate").header("Authorization", ownerToken).contentType(MediaType.APPLICATION_JSON).content(body));
        String runs = mvc.perform(get("/api/knowledge-bases/" + kb + "/retrieval/runs").header("Authorization", ownerToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].unanswerableRejectionRate").value(1.0)).andReturn().getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertEquals(com.jayway.jsonpath.JsonPath.<String>read(runs, "$.data[0].datasetHash"),
                com.jayway.jsonpath.JsonPath.<String>read(runs, "$.data[1].datasetHash"));
        mvc.perform(get("/api/knowledge-bases/" + kb + "/retrieval/runs").header("Authorization", aliceToken)).andExpect(status().isForbidden());
    }

    @Test void recentActivityIsLimitedToTheUsersOwnSessionsAndAccessibleBases() throws Exception {
        unanswered(alice, "共享库的问题");
        unanswered(bob, "别人的问题");
        jdbc.update("INSERT INTO document(knowledge_base_id,file_name,file_type,file_size,file_path,status) VALUES (?,'shared.md','md',1,'x','FAILED')", kb);
        jdbc.update("INSERT INTO document(knowledge_base_id,file_name,file_type,file_size,file_path,status) VALUES (?,'secret.md','md',1,'x','SUCCESS')", privateKb);
        mvc.perform(get("/api/dashboard/recent").header("Authorization", aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sessions.length()").value(1))
                .andExpect(jsonPath("$.data.sessions[0].knowledgeBaseId").value(kb))
                .andExpect(jsonPath("$.data.documents[*].fileName", hasItem("shared.md")))
                .andExpect(jsonPath("$.data.documents[*].fileName", not(hasItem("secret.md"))))
                .andExpect(jsonPath("$.data.failedDocuments[0].fileName").value("shared.md"))
                .andExpect(jsonPath("$.data.dueReviewCount").value(0));
    }

    @Test void answersKeepTheirRetrievalTraceExceptSmallTalk() throws Exception {
        var principal = new JwtPrincipal(alice.getId(), alice.getUsername(), "USER");
        var answer = chat.answer(kb, "完全无关的生僻问题", principal, null);
        chat.answer(kb, "你好", principal, answer.sessionId());
        mvc.perform(get("/api/chat/sessions/" + answer.sessionId() + "/messages").header("Authorization", aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[1].mode").value("INSUFFICIENT_CONTEXT"))
                .andExpect(jsonPath("$.data[1].retrieval.sufficient").value(false))
                .andExpect(jsonPath("$.data[1].retrieval.topK").value(5))
                .andExpect(jsonPath("$.data[1].retrieval.rewrittenQuery").isNotEmpty())
                .andExpect(jsonPath("$.data[3].mode").value("SMALL_TALK"))
                .andExpect(jsonPath("$.data[3].retrieval").doesNotExist());
    }
}
