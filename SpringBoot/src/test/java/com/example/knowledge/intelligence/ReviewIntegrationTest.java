package com.example.knowledge.intelligence;
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
import org.springframework.http.MediaType;
import java.util.UUID;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
class ReviewIntegrationTest {
 @Autowired MockMvc mvc;@Autowired JdbcTemplate jdbc;@Autowired SysUserMapper users;@Autowired JwtTokenProvider tokens;@Autowired ObjectMapper json;
 private SysUser user(){var u=new SysUser();u.setUsername(UUID.randomUUID().toString());u.setPassword("unused");u.setRole("USER");u.setStatus(1);users.insert(u);return u;}
 @Test void addsOwnedStudyOnceSchedulesReviewAndRejectsForeignReview() throws Exception {
  var u=user();var other=user();String auth="Bearer "+tokens.createToken(u);
  jdbc.update("INSERT INTO knowledge_base(name,owner_id) VALUES ('review',?)",u.getId());long kb=jdbc.queryForObject("SELECT id FROM knowledge_base WHERE owner_id=?",Long.class,u.getId());
  jdbc.update("INSERT INTO study_session(user_id,knowledge_base_id,title,mode,document_ids_json,sources_json,source_notice,cards_json,questions_json) VALUES (?,?,'review','LOCAL_DEMO','[]','[]','','[{\"front\":\"问题\",\"back\":\"答案\"}]','[]')",u.getId(),kb);
  long study=jdbc.queryForObject("SELECT id FROM study_session WHERE user_id=?",Long.class,u.getId());
  mvc.perform(post("/api/study/"+study+"/review-plan").header("Authorization",auth)).andExpect(status().isOk());
  mvc.perform(post("/api/study/"+study+"/review-plan").header("Authorization",auth)).andExpect(status().isOk());
  var result=mvc.perform(get("/api/review").header("Authorization",auth)).andExpect(jsonPath("$.data.length()").value(1)).andReturn().getResponse().getContentAsString();
  long id=json.readTree(result).path("data").path(0).path("id").asLong();
  mvc.perform(post("/api/review/"+id).header("Authorization","Bearer "+tokens.createToken(other)).contentType(MediaType.APPLICATION_JSON).content("{\"rating\":\"GOOD\"}")).andExpect(status().isNotFound());
  mvc.perform(post("/api/review/"+id).header("Authorization",auth).contentType(MediaType.APPLICATION_JSON).content("{\"rating\":\"GOOD\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.data.intervalDays").value(1));
  mvc.perform(get("/api/review").header("Authorization",auth)).andExpect(jsonPath("$.data").isEmpty());
  mvc.perform(post("/api/review/"+id).header("Authorization",auth).contentType(MediaType.APPLICATION_JSON).content("{\"rating\":\"GOOD\"}")).andExpect(status().isConflict());
 }
}
