package com.example.knowledge.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.knowledge.common.BusinessException;
import com.example.knowledge.common.ErrorCode;
import com.example.knowledge.entity.SysUser;
import com.example.knowledge.dto.RegisterRequest;
import com.example.knowledge.mapper.SysUserMapper;
import com.example.knowledge.security.JwtTokenProvider;
import com.example.knowledge.vo.LoginVO;
import com.example.knowledge.vo.UserVO;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
    private final SysUserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;

    public AuthService(SysUserMapper userMapper, PasswordEncoder passwordEncoder, JwtTokenProvider tokenProvider) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
    }

    public LoginVO login(String username, String password) {
        SysUser user = userMapper.selectOne(new QueryWrapper<SysUser>().eq("username", username));
        if (user == null || user.getStatus() == null || user.getStatus() != 1 || !passwordEncoder.matches(password, user.getPassword())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "用户名或密码错误");
        }
        return new LoginVO(tokenProvider.createToken(user), toVO(user));
    }

    public UserVO getUser(Long id) {
        SysUser user = userMapper.selectById(id);
        if (user == null || user.getStatus() == null || user.getStatus() != 1) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return toVO(user);
    }

    @org.springframework.transaction.annotation.Transactional
    public LoginVO register(RegisterRequest request) {
        if (!request.password().equals(request.confirmPassword())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "两次输入的密码不一致");
        }
        if (request.password().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "密码的UTF-8编码不能超过72字节");
        }
        if (userMapper.selectCount(new QueryWrapper<SysUser>().eq("username", request.username())) > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "用户名已存在");
        }
        SysUser user = new SysUser();
        user.setUsername(request.username());
        user.setNickname(request.nickname() == null || request.nickname().isBlank() ? request.username() : request.nickname());
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setRole("USER");
        user.setStatus(1);
        user.setTokenVersion(0);
        try { userMapper.insert(user); }
        catch (org.springframework.dao.DuplicateKeyException duplicate) {
            // The database unique constraint also protects concurrent registrations.
            throw new BusinessException(ErrorCode.CONFLICT, "用户名已存在");
        }
        return new LoginVO(tokenProvider.createToken(user), toVO(user));
    }

    @org.springframework.transaction.annotation.Transactional
    public void changePassword(Long id, String currentPassword, String newPassword) {
        SysUser user = userMapper.selectById(id);
        if (user == null || !passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "当前密码不正确");
        }
        if (newPassword.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "新密码的UTF-8编码不能超过72字节");
        }
        userMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<SysUser>()
                .eq("id", id).set("password", passwordEncoder.encode(newPassword))
                .setSql("token_version = token_version + 1"));
    }

    public UserVO updateProfile(Long id, String nickname) {
        userMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<SysUser>()
                .eq("id", id).set("nickname", nickname.trim()));
        return getUser(id);
    }

    public void logout(Long id) {
        userMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<SysUser>()
                .eq("id", id).setSql("token_version = token_version + 1"));
    }

    private UserVO toVO(SysUser user) {
        return new UserVO(user.getId(), user.getUsername(), user.getNickname(), user.getRole(), user.getStatus());
    }
}
