package com.example.knowledge;

import com.example.knowledge.entity.SysUser;
import com.example.knowledge.mapper.SysUserMapper;
import com.example.knowledge.security.JwtTokenProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PlatformSecurityIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired SysUserMapper users;
    @Autowired JwtTokenProvider tokens;
    @Autowired PasswordEncoder encoder;
    @Autowired ObjectMapper json;
    SysUser admin;
    SysUser student;
    String adminToken;
    String studentToken;

    @BeforeEach void seed() {
        admin = user("ADMIN"); student = user("USER");
        adminToken = tokens.createToken(admin); studentToken = tokens.createToken(student);
    }

    private SysUser user(String role) {
        SysUser user = new SysUser(); user.setUsername(UUID.randomUUID().toString());
        user.setPassword(encoder.encode("password123")); user.setRole(role); user.setStatus(1);
        users.insert(user); return user;
    }

    @Test void disabledUserCannotReuseIssuedToken() throws Exception {
        student.setStatus(0); users.updateById(student);
        mvc.perform(get("/api/knowledge-bases").header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value(401));
    }

    @Test void downgradedAdminCannotReuseOldRole() throws Exception {
        admin.setRole("USER"); users.updateById(admin);
        mvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(403));
    }

    @Test void passwordChangeInvalidatesPreviousToken() throws Exception {
        mvc.perform(put("/api/auth/password").header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("currentPassword", "password123", "newPassword", "anotherPass456"))))
                .andExpect(status().isOk());
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + studentToken)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("username", student.getUsername(), "password", "anotherPass456"))))
                .andExpect(status().isOk());
    }

    @Test void logoutRevokesToken() throws Exception {
        mvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + studentToken)).andExpect(status().isOk());
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + studentToken)).andExpect(status().isUnauthorized());
    }

    @Test void adminCreatesUserAndCannotDisableSelf() throws Exception {
        mvc.perform(post("/api/admin/users").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("username", "u" + UUID.randomUUID(), "password", "password123", "nickname", "Tester", "role", "USER"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value(1)).andExpect(jsonPath("$.data.password").doesNotExist());
        mvc.perform(put("/api/admin/users/" + admin.getId() + "/status").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":0}"))
                .andExpect(status().isBadRequest());
    }

    @Test void ordinaryDashboardDoesNotExposeOtherUsersKnowledge() throws Exception {
        mvc.perform(post("/api/knowledge-bases").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Private admin knowledge\"}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/dashboard/overview").header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.knowledgeBaseCount").value(0));
    }
}
