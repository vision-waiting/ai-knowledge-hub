package com.example.knowledge.config;

import com.example.knowledge.entity.SysUser;
import com.example.knowledge.mapper.SysUserMapper;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@Profile("dev")
public class DemoDataInitializer implements CommandLineRunner {
    private final SysUserMapper userMapper; private final PasswordEncoder passwordEncoder;
    public DemoDataInitializer(SysUserMapper userMapper, PasswordEncoder passwordEncoder) { this.userMapper = userMapper; this.passwordEncoder = passwordEncoder; }
    @Override public void run(String... args) {
        if (userMapper.selectCount(null) > 0) return;
        insert("admin", "123456", "管理员", "ADMIN"); insert("student", "123456", "学生", "USER");
    }
    private void insert(String username, String password, String nickname, String role) { SysUser user = new SysUser(); user.setUsername(username); user.setPassword(passwordEncoder.encode(password)); user.setNickname(nickname); user.setRole(role); user.setStatus(1); userMapper.insert(user); }
}

