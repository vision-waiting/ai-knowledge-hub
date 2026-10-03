package com.example.knowledge.websource;
import com.example.knowledge.entity.SysUser;
import com.example.knowledge.mapper.SysUserMapper;
import com.example.knowledge.security.JwtTokenProvider;
import com.example.knowledge.document.DocumentProcessWorker;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import java.util.UUID;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
class WebSourceIntegrationTest {
 @Autowired MockMvc mvc;@Autowired JdbcTemplate jdbc;@Autowired SysUserMapper users;@Autowired JwtTokenProvider tokens;@Autowired ObjectMapper json;
 @MockitoBean PublicPageFetcher fetcher;@MockitoBean DocumentProcessWorker worker;
 @Test void importsWithoutDuplicationAndChangedPageCreatesVersion() throws Exception {
  var u=new SysUser();u.setUsername(UUID.randomUUID().toString());u.setPassword("unused");u.setRole("USER");u.setStatus(1);users.insert(u);
  jdbc.update("INSERT INTO knowledge_base(name,owner_id) VALUES ('web sources',?)",u.getId());long kb=jdbc.queryForObject("SELECT id FROM knowledge_base WHERE owner_id=?",Long.class,u.getId());
  String auth="Bearer "+tokens.createToken(u),url="/api/knowledge-bases/"+kb+"/web-sources",remote="https://example.com/manual";
  when(fetcher.fetch(remote)).thenReturn(new PublicPageFetcher.Page(remote,"手册","first content"));
  var result=mvc.perform(post(url).header("Authorization",auth).contentType(MediaType.APPLICATION_JSON).content("{\"url\":\""+remote+"\"}")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
  long id=json.readTree(result).path("data").path("id").asLong(),doc=json.readTree(result).path("data").path("documentId").asLong();
  jdbc.update("UPDATE document SET status='SUCCESS' WHERE id=?",doc);
  mvc.perform(post(url+"/"+id+"/check").header("Authorization",auth)).andExpect(status().isOk()).andExpect(jsonPath("$.data.documentId").value(doc));
  when(fetcher.fetch(remote)).thenReturn(new PublicPageFetcher.Page(remote,"手册","updated content"));
  mvc.perform(post(url+"/"+id+"/check").header("Authorization",auth)).andExpect(status().isOk()).andExpect(jsonPath("$.data.documentId",org.hamcrest.Matchers.not((int)doc)));
  org.junit.jupiter.api.Assertions.assertEquals("SUCCESS",jdbc.queryForObject("SELECT status FROM document WHERE id=?",String.class,doc));
  mvc.perform(get(url).header("Authorization",auth)).andExpect(jsonPath("$.data.length()").value(1));
 }
}
