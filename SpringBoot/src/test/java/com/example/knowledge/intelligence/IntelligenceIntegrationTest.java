package com.example.knowledge.intelligence;

import com.example.knowledge.entity.*;
import com.example.knowledge.mapper.*;
import com.example.knowledge.security.JwtTokenProvider;
import com.fasterxml.jackson.databind.JsonNode;
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

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class IntelligenceIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired SysUserMapper users;
    @Autowired KnowledgeBaseMapper bases;
    @Autowired DocumentMapper documents;
    @Autowired DocumentChunkMapper chunks;
    @Autowired JwtTokenProvider tokens;
    @Autowired JdbcTemplate jdbc;
    String token, otherToken;
    Long kb, doc, second;

    @BeforeEach void seed() {
        SysUser user = user(); token = tokens.createToken(user); otherToken = tokens.createToken(user());
        KnowledgeBase base = new KnowledgeBase(); base.setOwnerId(user.getId()); base.setName("学习资料"); base.setStatus(1);
        bases.insert(base); kb = base.getId();
        doc = document("缓存说明.txt", "缓存可以减少数据库请求。缓存过期策略包括定时过期。写入数据后应更新缓存。");
        second = document("队列说明.txt", "队列能够缓冲突发流量。消费者按顺序处理消息。失败消息需要重试机制。");
    }
    private SysUser user() {
        SysUser u = new SysUser(); u.setUsername(UUID.randomUUID().toString()); u.setPassword("unused"); u.setRole("USER"); u.setStatus(1); users.insert(u); return u;
    }
    private Long document(String name, String text) {
        Document d = new Document(); d.setKnowledgeBaseId(kb); d.setFileName(name); d.setFilePath("unused"); d.setStatus("SUCCESS"); documents.insert(d);
        DocumentChunk c = new DocumentChunk(); c.setDocumentId(d.getId()); c.setKnowledgeBaseId(kb); c.setChunkIndex(0); c.setPageNumber(1); c.setContent(text); chunks.insert(c); return d.getId();
    }
    private JsonNode createStudy() throws Exception {
        return json.readTree(mvc.perform(post("/api/knowledge-bases/" + kb + "/study").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("documentIds", List.of(doc), "questionCount", 3))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.mode").value("LOCAL_DEMO"))
                .andExpect(jsonPath("$.data.questions[0].correctIndex").doesNotExist())
                .andReturn().getResponse().getContentAsString()).path("data");
    }

    @Test void analysisPersistsAndEnforcesPermissionsAndCardinality() throws Exception {
        String path = "/api/knowledge-bases/" + kb + "/analysis";
        String body = json.writeValueAsString(Map.of("type", "COMPARE", "documentIds", List.of(doc, second), "focus", "使用场景"));
        mvc.perform(post(path).header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.mode").value("LOCAL_DEMO"))
                .andExpect(jsonPath("$.data.sourceNotice").isNotEmpty()).andExpect(jsonPath("$.data.sources.length()").value(2));
        mvc.perform(get(path).header("Authorization", "Bearer " + token)).andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(1));
        mvc.perform(get(path).header("Authorization", "Bearer " + otherToken)).andExpect(status().isForbidden());
        mvc.perform(post(path).header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("type", "COMPARE", "documentIds", List.of(doc)))))
                .andExpect(status().isBadRequest());
    }

    @Test void studyDoesNotLeakKeysUntilSubmissionAndPersistsMistakesAndAttempts() throws Exception {
        JsonNode study = createStudy(); long id = study.path("id").asLong();
        assertThat(study.path("cards").size()).isGreaterThanOrEqualTo(3);
        mvc.perform(get("/api/study/" + id).header("Authorization", "Bearer " + otherToken)).andExpect(status().isNotFound());
        JsonNode graded = json.readTree(mvc.perform(post("/api/study/" + id + "/submit").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"answers\":[0,0,0]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(3))
                .andReturn().getResponse().getContentAsString()).path("data");
        long correct = 0;
        for (JsonNode result : graded.path("results")) if (result.path("correct").asBoolean()) correct++;
        assertThat(graded.path("score").asLong()).isEqualTo(correct);
        assertThat(correct).isLessThan(3);
        mvc.perform(get("/api/study/" + id).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.questions[0].correctIndex").doesNotExist());
        mvc.perform(get("/api/study/" + id + "/attempts").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(1));
        JsonNode mistakes = json.readTree(mvc.perform(get("/api/study/mistakes").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("data");
        assertThat(mistakes.size()).isEqualTo(3 - correct);
        long mistake = mistakes.path(0).path("id").asLong();
        mvc.perform(put("/api/study/mistakes/" + mistake + "/resolve").header("Authorization", "Bearer " + otherToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"resolved\":true}")).andExpect(status().isNotFound());
        mvc.perform(put("/api/study/mistakes/" + mistake + "/resolve").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"resolved\":true}")).andExpect(status().isOk());
        mvc.perform(get("/api/study/mistakes").header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(0));
        mvc.perform(get("/api/study").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test void savedStudySurvivesSourceArchivalButCannotGenerateFromOldVersion() throws Exception {
        long id = createStudy().path("id").asLong();
        for (String state : List.of("ARCHIVED", "TRASHED")) {
            jdbc.update("UPDATE document SET status=? WHERE id=?", state, doc);
            mvc.perform(get("/api/study/" + id).header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk());
            mvc.perform(post("/api/knowledge-bases/" + kb + "/study").header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("documentIds", List.of(doc), "questionCount", 3))))
                    .andExpect(status().isConflict());
        }
        mvc.perform(get("/api/study/" + id).header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound());
    }

    @Test void sourceReadinessAndAnswerBoundsAreValidated() throws Exception {
        JsonNode study = createStudy(); long id = study.path("id").asLong();
        for (String answers : List.of("[0]", "[0,0,9]", "[null,0,0]")) {
            mvc.perform(post("/api/study/" + id + "/submit").header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON).content("{\"answers\":" + answers + "}"))
                    .andExpect(status().isBadRequest());
        }
        jdbc.update("UPDATE document SET status='PROCESSING' WHERE id=?", doc);
        mvc.perform(post("/api/knowledge-bases/" + kb + "/study").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("documentIds", List.of(doc), "questionCount", 3))))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/study/" + id).header("Authorization", "Bearer " + token)).andExpect(status().isConflict());
    }

    @Test void longDocumentRedistributesUnusedChunkBudgetAndRetainsTail() throws Exception {
        String longText = "长".repeat(30000);
        String tailText = "短小尾段也应当被保留";
        jdbc.update("UPDATE document_chunk SET content=? WHERE document_id=?", longText, doc);
        DocumentChunk tail = new DocumentChunk(); tail.setDocumentId(doc); tail.setKnowledgeBaseId(kb);
        tail.setChunkIndex(1); tail.setPageNumber(2); tail.setContent(tailText); chunks.insert(tail);
        mvc.perform(post("/api/knowledge-bases/" + kb + "/analysis").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("type", "SUMMARY", "documentIds", List.of(doc)))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.sources[0].includedCharacters").value(20000))
                .andExpect(jsonPath("$.data.sources[0].totalCharacters").value(30010))
                .andExpect(jsonPath("$.data.sources[0].includedChunks").value(2))
                .andExpect(jsonPath("$.data.content").value(org.hamcrest.Matchers.containsString("长".repeat(19990))))
                .andExpect(jsonPath("$.data.content").value(org.hamcrest.Matchers.containsString(tailText)))
                .andExpect(jsonPath("$.data.sourceNotice").value(org.hamcrest.Matchers.containsString("不能视为全文结论")));
    }

    @Test void shortDocumentIncludesUnevenChunksAndRestoresCoverage() throws Exception {
        String longText = "完整保留原文段落".repeat(100);
        jdbc.update("UPDATE document_chunk SET content=? WHERE document_id=?", longText, doc);
        DocumentChunk tail = new DocumentChunk(); tail.setDocumentId(doc); tail.setKnowledgeBaseId(kb);
        tail.setChunkIndex(1); tail.setPageNumber(2); tail.setContent("短小尾段"); chunks.insert(tail);
        String path = "/api/knowledge-bases/" + kb + "/analysis";
        mvc.perform(post(path).header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("type", "SUMMARY", "documentIds", List.of(doc)))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.sources[0].includedCharacters").value(longText.length() + 4))
                .andExpect(jsonPath("$.data.content").value(org.hamcrest.Matchers.containsString(longText)));
        mvc.perform(get(path).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].sources[0].includedCharacters").value(longText.length() + 4));
        jdbc.update("DELETE FROM document WHERE id=?", doc);
        mvc.perform(get(path).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test void rejectsCrossBaseDuplicateAndEmptySources() throws Exception {
        String path = "/api/knowledge-bases/" + kb + "/analysis";
        for (List<Long> ids : List.of(List.of(doc, doc), List.<Long>of())) {
            mvc.perform(post(path).header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(Map.of("type", "SUMMARY", "documentIds", ids))))
                    .andExpect(status().isBadRequest());
        }
        jdbc.update("UPDATE document SET knowledge_base_id=? WHERE id=?", kb + 900000, second);
        mvc.perform(post(path).header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("type", "SUMMARY", "documentIds", List.of(second)))))
                .andExpect(status().isNotFound());
        jdbc.update("DELETE FROM document_chunk WHERE document_id=?", doc);
        mvc.perform(post(path).header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("type", "SUMMARY", "documentIds", List.of(doc)))))
                .andExpect(status().isUnprocessableEntity());
    }
}
