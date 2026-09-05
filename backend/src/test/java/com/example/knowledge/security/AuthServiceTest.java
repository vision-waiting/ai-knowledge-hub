package com.example.knowledge.security;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.knowledge.entity.SysUser;
import com.example.knowledge.mapper.SysUserMapper;
import com.example.knowledge.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthServiceTest {
    @Test
    void bcryptPasswordIsVerifiedAndJwtContainsUserIdentity() {
        SysUser user = new SysUser();
        user.setId(7L);
        user.setUsername("student");
        user.setPassword(new BCryptPasswordEncoder().encode("secret"));
        user.setRole("USER");
        user.setStatus(1);

        SysUserMapper mapper = mock(SysUserMapper.class);
        when(mapper.selectOne(any())).thenReturn(user);
        JwtTokenProvider tokenProvider = new JwtTokenProvider("test-secret-that-is-long-enough-for-hs256", 24);
        AuthService service = new AuthService(mapper, new BCryptPasswordEncoder(), tokenProvider);

        var result = service.login("student", "secret");

        assertEquals("student", result.user().username());
        assertEquals(7L, tokenProvider.parse(result.token()).userId());
        assertEquals("USER", tokenProvider.parse(result.token()).role());
    }

    @Test
    void invalidPasswordIsRejected() {
        SysUser user = new SysUser();
        user.setUsername("student");
        user.setPassword(new BCryptPasswordEncoder().encode("secret"));
        user.setRole("USER");
        user.setStatus(1);
        SysUserMapper mapper = mock(SysUserMapper.class);
        when(mapper.selectOne(any())).thenReturn(user);
        AuthService service = new AuthService(mapper, new BCryptPasswordEncoder(), new JwtTokenProvider("test-secret-that-is-long-enough-for-hs256", 24));

        assertThrows(RuntimeException.class, () -> service.login("student", "wrong"));
    }
}

