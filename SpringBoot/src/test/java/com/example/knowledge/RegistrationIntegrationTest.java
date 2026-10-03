package com.example.knowledge;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.knowledge.entity.SysUser;
import com.example.knowledge.mapper.SysUserMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RegistrationIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired SysUserMapper users;
    @Autowired PasswordEncoder encoder;
    private String username() { return "user_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16); }
    private String body(String name, String password, String confirmation) throws Exception {
        return json.writeValueAsString(Map.of("username", name, "password", password, "confirmPassword", confirmation, "nickname", "新用户", "role", "ADMIN", "status", 0));
    }

    @Test void registersOrdinaryUserWithEncryptedPasswordAndWorkingSession() throws Exception {
        String name = username();
        var response = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body(name, "password123", "password123")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.user.role").value("USER"))
                .andExpect(jsonPath("$.data.user.status").value(1)).andExpect(jsonPath("$.data.user.password").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        SysUser saved = users.selectOne(new QueryWrapper<SysUser>().eq("username", name));
        assertNotEquals("password123", saved.getPassword());
        assertTrue(encoder.matches("password123", saved.getPassword()));
        String token = json.readTree(response).path("data").path("token").asText();
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token)).andExpect(status().isOk()).andExpect(jsonPath("$.data.username").value(name));
        mvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
        String libraries = mvc.perform(get("/api/knowledge-bases").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        // New users may read explicitly shared libraries as well as administrator libraries.
        for (var library : json.readTree(libraries).path("data")) {
            assertTrue(library.path("shared").asBoolean() || "ADMIN".equals(users.selectById(library.path("ownerId").asLong()).getRole()));
        }
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("username", name, "password", "password123")))).andExpect(status().isOk());
    }

    @Test void duplicateUsernameDoesNotReplacePassword() throws Exception {
        String name = username();
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body(name, "password123", "password123"))).andExpect(status().isOk());
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body(name, "otherpass456", "otherpass456"))).andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("用户名已存在"));
        assertTrue(encoder.matches("password123", users.selectOne(new QueryWrapper<SysUser>().eq("username", name)).getPassword()));
    }

    @ParameterizedTest
    @CsvSource({"abc,password123,password123", "bad-name,password123,password123", "valid_name,short,short", "valid_name,password123,different123"})
    void rejectsInvalidRegistration(String name, String password, String confirmation) throws Exception {
        long before = users.selectCount(null);
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body(name, password, confirmation))).andExpect(status().isBadRequest());
        assertEquals(before, users.selectCount(null));
    }

    @Test void rejectsPasswordsExceedingBcryptByteLimit() throws Exception {
        String password = "密".repeat(25);
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body(username(), password, password))).andExpect(status().isBadRequest());
    }
}
