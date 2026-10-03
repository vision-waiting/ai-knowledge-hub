package com.example.knowledge;

import com.example.knowledge.entity.SysUser;
import com.example.knowledge.mapper.SysUserMapper;
import com.example.knowledge.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AdminUserDetailsIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired SysUserMapper users;
    @Autowired JwtTokenProvider tokens;
    SysUser admin, user;
    String auth;
    @BeforeEach void setup() { admin = seed("ADMIN"); user = seed("USER"); auth = "Bearer " + tokens.createToken(admin); }
    SysUser seed(String role) {
        SysUser u = new SysUser(); u.setUsername("u" + java.util.UUID.randomUUID().toString().replace("-", ""));
        u.setPassword("private-hash"); u.setNickname("Original"); u.setRole(role); u.setStatus(1); u.setTokenVersion(0); users.insert(u); return u;
    }
    String body(String name, String role, int status) { return "{\"username\":\"" + name + "\",\"nickname\":\"Updated\",\"role\":\"" + role + "\",\"status\":" + status + "}"; }
    @Test void adminCanViewAndEditWithoutExposingOrChangingPassword() throws Exception {
        mvc.perform(get("/api/admin/users/" + user.getId()).header("Authorization", auth))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.username").value(user.getUsername()))
            .andExpect(jsonPath("$.data.createdAt").exists()).andExpect(jsonPath("$.data.password").doesNotExist());
        mvc.perform(put("/api/admin/users/" + user.getId()).header("Authorization", auth).contentType("application/json")
            .content(body("renamed_user", "USER", 1))).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.nickname").value("Updated")).andExpect(jsonPath("$.data.username").value("renamed_user"));
        assertEquals("private-hash", users.selectById(user.getId()).getPassword());
        assertEquals(0, users.selectById(user.getId()).getTokenVersion());
    }
    @Test void roleAndStatusChangeRevokesSession() throws Exception {
        String oldToken = "Bearer " + tokens.createToken(user);
        mvc.perform(put("/api/admin/users/" + user.getId()).header("Authorization", auth).contentType("application/json")
            .content(body(user.getUsername(), "USER", 0))).andExpect(status().isOk());
        assertEquals(1, users.selectById(user.getId()).getTokenVersion());
        mvc.perform(get("/api/auth/me").header("Authorization", oldToken)).andExpect(status().isUnauthorized());
    }
    @Test void rejectsDuplicateNameInvalidValuesAndSelfLockoutAtomically() throws Exception {
        mvc.perform(put("/api/admin/users/" + user.getId()).header("Authorization", auth).contentType("application/json")
            .content(body(admin.getUsername(), "USER", 0))).andExpect(status().isConflict());
        assertEquals("USER", users.selectById(user.getId()).getRole());
        for (String invalid : new String[]{body("!", "USER", 1), body(user.getUsername(), "ROOT", 1), body(user.getUsername(), "USER", 2)}) {
            mvc.perform(put("/api/admin/users/" + user.getId()).header("Authorization", auth).contentType("application/json").content(invalid)).andExpect(status().isBadRequest());
        }
        for (String invalid : new String[]{body(admin.getUsername(), "USER", 1), body(admin.getUsername(), "USER", 0)}) {
            mvc.perform(put("/api/admin/users/" + admin.getId()).header("Authorization", auth).contentType("application/json").content(invalid)).andExpect(status().isBadRequest());
        }
    }
    @Test void cannotPromoteOrdinaryUserThroughEitherEndpoint() throws Exception {
        mvc.perform(put("/api/admin/users/" + user.getId()).header("Authorization", auth).contentType("application/json")
            .content(body(user.getUsername(), "ADMIN", 1))).andExpect(status().isBadRequest());
        mvc.perform(put("/api/admin/users/" + user.getId() + "/role").header("Authorization", auth).contentType("application/json")
            .content("{\"role\":\"ADMIN\"}")).andExpect(status().isBadRequest());
        assertEquals("USER", users.selectById(user.getId()).getRole());
    }
    @Autowired org.springframework.security.crypto.password.PasswordEncoder encoder;
    @Test void administratorCanResetPasswordAndRevokeOldSession() throws Exception {
        String oldToken = "Bearer " + tokens.createToken(user);
        String payload = body(user.getUsername(), "USER", 1).replace("}", ",\"password\":\"newPassword123\"}");
        mvc.perform(put("/api/admin/users/" + user.getId()).header("Authorization", auth).contentType("application/json")
            .content(payload)).andExpect(status().isOk()).andExpect(jsonPath("$.data.password").doesNotExist());
        assertTrue(encoder.matches("newPassword123", users.selectById(user.getId()).getPassword()));
        mvc.perform(get("/api/auth/me").header("Authorization", oldToken)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").contentType("application/json")
            .content("{\"username\":\"" + user.getUsername() + "\",\"password\":\"newPassword123\"}")).andExpect(status().isOk());
    }
    @Test void invalidPasswordDoesNotChangeProfile() throws Exception {
        for (String password : new String[]{"short", "密".repeat(25)}) {
            String payload = body(user.getUsername(), "USER", 1).replace("}", ",\"password\":\"" + password + "\"}");
            mvc.perform(put("/api/admin/users/" + user.getId()).header("Authorization", auth).contentType("application/json")
                .content(payload)).andExpect(status().isBadRequest());
            assertEquals("Original", users.selectById(user.getId()).getNickname());
        }
    }
    @Test void ordinaryUserCannotReadOrEditAndMissingUserIsNotFound() throws Exception {
        String ordinary = "Bearer " + tokens.createToken(user);
        mvc.perform(get("/api/admin/users/" + admin.getId()).header("Authorization", ordinary)).andExpect(status().isForbidden());
        mvc.perform(put("/api/admin/users/" + user.getId()).header("Authorization", ordinary).contentType("application/json")
            .content(body(user.getUsername(), "ADMIN", 1))).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/users/9223372036854775807").header("Authorization", auth)).andExpect(status().isNotFound());
        mvc.perform(put("/api/admin/users/9223372036854775807").header("Authorization", auth).contentType("application/json")
            .content(body("missing", "USER", 1))).andExpect(status().isNotFound());
    }
}
