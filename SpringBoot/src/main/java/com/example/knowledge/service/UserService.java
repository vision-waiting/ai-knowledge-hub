package com.example.knowledge.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.example.knowledge.common.BusinessException;
import com.example.knowledge.common.ErrorCode;
import com.example.knowledge.dto.UserCreateRequest;
import com.example.knowledge.entity.SysUser;
import com.example.knowledge.mapper.SysUserMapper;
import com.example.knowledge.security.JwtPrincipal;
import com.example.knowledge.vo.UserVO;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Service
public class UserService {
    private final SysUserMapper users;
    private final PasswordEncoder encoder;
    public UserService(SysUserMapper users, PasswordEncoder encoder) { this.users = users; this.encoder = encoder; }
    public List<UserVO> list() { return users.selectList(new QueryWrapper<SysUser>().orderByDesc("id")).stream().map(this::view).toList(); }

    public UserVO get(Long id) { require(id); return view(users.selectById(id)); }

    @Transactional
    public UserVO update(Long id, com.example.knowledge.dto.UserUpdateRequest request, JwtPrincipal actor) {
        SysUser user = users.selectOne(new QueryWrapper<SysUser>().eq("id", id).last("FOR UPDATE"));
        if (user == null) throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        rejectPromotion(user, request.role());
        boolean changePassword = request.password() != null && !request.password().isEmpty();
        if (changePassword && (request.password().length() < 8 || request.password().getBytes(StandardCharsets.UTF_8).length > 72)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "密码至少8位，UTF-8编码不能超过72字节");
        }
        if (id.equals(actor.userId())) {
            if (request.status() == 0) throw new BusinessException(ErrorCode.BAD_REQUEST, "不能禁用当前登录账号");
            if (!"ADMIN".equals(request.role())) throw new BusinessException(ErrorCode.BAD_REQUEST, "不能撤销当前账号的管理员角色");
        }
        UpdateWrapper<SysUser> update = new UpdateWrapper<SysUser>().eq("id", id)
                .set("username", request.username()).set("nickname", request.nickname() == null ? "" : request.nickname().trim())
                .set("role", request.role()).set("status", request.status()).set("updated_at", java.time.LocalDateTime.now());
        if (changePassword) update.set("password", encoder.encode(request.password()));
        if (changePassword || !request.role().equals(user.getRole()) || !request.status().equals(user.getStatus())) {
            update.setSql("token_version = COALESCE(token_version, 0) + 1");
        }
        try { users.update(null, update); }
        catch (org.springframework.dao.DuplicateKeyException exception) { throw new BusinessException(ErrorCode.CONFLICT, "用户名已存在"); }
        return get(id);
    }

    @Transactional
    public UserVO create(UserCreateRequest request) {
        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72) throw new BusinessException(ErrorCode.BAD_REQUEST, "密码不能超过72字节");
        SysUser user = new SysUser(); user.setUsername(request.username()); user.setPassword(encoder.encode(request.password()));
        user.setNickname(request.nickname()); user.setRole(request.role()); user.setStatus(1); user.setTokenVersion(0);
        try { users.insert(user); }
        catch (org.springframework.dao.DuplicateKeyException exception) { throw new BusinessException(ErrorCode.CONFLICT, "用户名已存在"); }
        return view(user);
    }

    @Transactional
    public void status(Long id, int status, JwtPrincipal actor) {
        require(id);
        if (id.equals(actor.userId()) && status == 0) throw new BusinessException(ErrorCode.BAD_REQUEST, "不能禁用当前登录账号");
        users.update(null, new UpdateWrapper<SysUser>().eq("id", id).set("status", status).setSql("token_version = token_version + 1"));
    }

    @Transactional
    public void role(Long id, String role, JwtPrincipal actor) {
        SysUser user = users.selectOne(new QueryWrapper<SysUser>().eq("id", id).last("FOR UPDATE"));
        if (user == null) throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        rejectPromotion(user, role);
        if (id.equals(actor.userId()) && !"ADMIN".equals(role)) throw new BusinessException(ErrorCode.BAD_REQUEST, "不能撤销当前账号的管理员角色");
        users.update(null, new UpdateWrapper<SysUser>().eq("id", id).set("role", role).setSql("token_version = token_version + 1"));
    }

    private void rejectPromotion(SysUser user, String role) {
        if (!"ADMIN".equals(user.getRole()) && "ADMIN".equals(role)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "不支持将普通用户修改为管理员");
        }
    }
    private void require(Long id) { if (users.selectById(id) == null) throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在"); }
    private UserVO view(SysUser user) { return new UserVO(user.getId(), user.getUsername(), user.getNickname(), user.getRole(), user.getStatus(), user.getCreatedAt(), user.getUpdatedAt()); }
}
