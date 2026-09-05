package com.example.knowledge;

import com.example.knowledge.entity.SysUser;
import com.example.knowledge.mapper.SysUserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiIntegrationTest {
    @Autowired MockMvc mockMvc;
    @Autowired SysUserMapper userMapper;
    @Autowired PasswordEncoder passwordEncoder;

    @BeforeEach
    void seedUser() {
        userMapper.delete(null);
        SysUser user = new SysUser(); user.setUsername("student"); user.setPassword(passwordEncoder.encode("123456")); user.setNickname("学生"); user.setRole("USER"); user.setStatus(1); userMapper.insert(user);
    }

    @Test
    void loginReturnsJwtAndUser() throws Exception {
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"student\",\"password\":\"123456\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200)).andExpect(jsonPath("$.data.token").isString()).andExpect(jsonPath("$.data.user.username").value("student"));
    }
}

