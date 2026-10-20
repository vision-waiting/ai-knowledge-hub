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
import org.springframework.http.MediaType;
import java.util.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
class ContentIntegrationTest {
 @Autowired MockMvc mvc; @Autowired JdbcTemplate jdbc; @Autowired SysUserMapper users; @Autowired JwtTokenProvider tokens; @Autowired ObjectMapper json;
 private SysUser user() { var u=new SysUser();u.setUsername(UUID.randomUUID().toString());u.setPassword("unused");u.setRole("USER");u.setStatus(1);users.insert(u);return u; }
 @Test void managerPublishesOrderedContentReadersCannotMutateAndSourcesStayScoped() throws Exception {
  var owner=user();var reader=user();var stranger=user();
  jdbc.update("INSERT INTO knowledge_base(name,owner_id,shared) VALUES ('content',?,true)",owner.getId());
  Long kb=jdbc.queryForObject("SELECT id FROM knowledge_base WHERE owner_id=?",Long.class,owner.getId());
  String url="/api/knowledge-bases/"+kb+"/content", auth="Bearer "+tokens.createToken(owner), read="Bearer "+tokens.createToken(reader);
  String payload=json.writeValueAsString(Map.of("kind","FAQ","title","Question?","body","Reviewed answer","sortOrder",2));
  String response=mvc.perform(post(url).header("Authorization",auth).contentType(MediaType.APPLICATION_JSON).content(payload)).andExpect(status().isOk()).andExpect(jsonPath("$.data.title").value("Question?")).andReturn().getResponse().getContentAsString();
  long id=json.readTree(response).path("data").path("id").asLong();
  mvc.perform(get(url).header("Authorization",read).param("kind","FAQ")).andExpect(status().isOk()).andExpect(jsonPath("$.data.canManage").value(false)).andExpect(jsonPath("$.data.items.length()").value(1));
  mvc.perform(post(url).header("Authorization",read).contentType(MediaType.APPLICATION_JSON).content(payload)).andExpect(status().isForbidden());
  mvc.perform(put(url+"/"+id).header("Authorization",read).contentType(MediaType.APPLICATION_JSON).content(payload)).andExpect(status().isForbidden());
  mvc.perform(delete(url+"/"+id).header("Authorization",read)).andExpect(status().isForbidden());
  jdbc.update("INSERT INTO document(knowledge_base_id,file_name,file_path,status) VALUES (?,'foreign','unused','SUCCESS')",kb+100000);
  Long doc=jdbc.queryForObject("SELECT id FROM document WHERE knowledge_base_id=?",Long.class,kb+100000);
  mvc.perform(post(url).header("Authorization",auth).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("kind","TERM","title","Term","body","Definition","sourceDocumentId",doc)))).andExpect(status().isBadRequest());
  mvc.perform(put(url+"/"+id).header("Authorization",auth).contentType(MediaType.APPLICATION_JSON).content("{\"kind\":\"TERM\",\"title\":\"Cache\",\"body\":\"Definition\",\"aliases\":\"缓存, cache\",\"sortOrder\":1,\"baseVersion\":1}")).andExpect(status().isOk()).andExpect(jsonPath("$.data.aliases").value("缓存, cache"));
  mvc.perform(get(url).header("Authorization",auth).param("kind","FAQ")).andExpect(status().isOk()).andExpect(jsonPath("$.data.items").isEmpty());
  mvc.perform(delete(url+"/"+id).header("Authorization",auth)).andExpect(status().isOk());
  jdbc.update("UPDATE knowledge_base SET shared=false WHERE id=?",kb);
  mvc.perform(get(url).header("Authorization","Bearer "+tokens.createToken(stranger))).andExpect(status().isForbidden());
 }
}
