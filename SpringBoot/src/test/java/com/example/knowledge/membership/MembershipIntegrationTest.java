package com.example.knowledge.membership;
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
import org.springframework.http.MediaType;
import java.util.UUID;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
class MembershipIntegrationTest {
 @Autowired MockMvc mvc; @Autowired JdbcTemplate jdbc; @Autowired SysUserMapper users; @Autowired JwtTokenProvider tokens;
 private SysUser user(){ var u=new SysUser();u.setUsername(UUID.randomUUID().toString());u.setPassword("unused");u.setRole("USER");u.setStatus(1);users.insert(u);return u; }
 @Test void rolesControlPrivateVisibilityWritesAndRevocation() throws Exception {
  var owner=user();var member=user();
  jdbc.update("INSERT INTO knowledge_base(name,owner_id) VALUES ('private team',?)",owner.getId());
  long kb=jdbc.queryForObject("SELECT id FROM knowledge_base WHERE owner_id=?",Long.class,owner.getId());
  String url="/api/knowledge-bases/"+kb, a="Bearer "+tokens.createToken(owner), b="Bearer "+tokens.createToken(member);
  mvc.perform(put(url+"/members").header("Authorization",a).contentType(MediaType.APPLICATION_JSON).content("{\"username\":\""+member.getUsername()+"\",\"role\":\"VIEWER\"}")).andExpect(status().isOk());
  mvc.perform(get(url).header("Authorization",b)).andExpect(status().isOk());
  mvc.perform(get("/api/knowledge-bases").header("Authorization",b)).andExpect(jsonPath("$.data[*].id",org.hamcrest.Matchers.hasItem((int)kb)));
  mvc.perform(get(url+"/permissions").header("Authorization",b)).andExpect(jsonPath("$.data.canWrite").value(false));
  mvc.perform(put(url+"/members").header("Authorization",b).contentType(MediaType.APPLICATION_JSON).content("{\"username\":\""+member.getUsername()+"\",\"role\":\"MANAGER\"}")).andExpect(status().isForbidden());
  mvc.perform(put(url+"/members").header("Authorization",a).contentType(MediaType.APPLICATION_JSON).content("{\"username\":\""+member.getUsername()+"\",\"role\":\"EDITOR\"}")).andExpect(status().isOk());
  mvc.perform(get(url+"/permissions").header("Authorization",b)).andExpect(jsonPath("$.data.canWrite").value(true)).andExpect(jsonPath("$.data.canManage").value(false));
  mvc.perform(delete(url).header("Authorization",b)).andExpect(status().isForbidden());
  mvc.perform(put(url+"/members").header("Authorization",a).contentType(MediaType.APPLICATION_JSON).content("{\"username\":\""+member.getUsername()+"\",\"role\":\"MANAGER\"}")).andExpect(status().isOk());
  mvc.perform(get(url+"/members").header("Authorization",b)).andExpect(status().isOk());
  mvc.perform(delete(url+"/members/"+member.getId()).header("Authorization",a)).andExpect(status().isOk());
  mvc.perform(get(url).header("Authorization",b)).andExpect(status().isForbidden());
  mvc.perform(get("/api/search").param("knowledgeBaseId",""+kb).param("q","secret").header("Authorization",b)).andExpect(status().isForbidden());
 }
}
