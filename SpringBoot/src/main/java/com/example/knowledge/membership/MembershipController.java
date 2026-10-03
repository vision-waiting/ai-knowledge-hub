package com.example.knowledge.membership;

import com.example.knowledge.common.*;
import com.example.knowledge.security.CurrentUser;
import com.example.knowledge.service.PermissionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController @RequestMapping("/api/knowledge-bases/{id}")
public class MembershipController {
    private final JdbcTemplate jdbc; private final PermissionService permissions;
    public MembershipController(JdbcTemplate jdbc, PermissionService permissions){this.jdbc=jdbc;this.permissions=permissions;}
    public enum Role { VIEWER, EDITOR, MANAGER }
    public record Member(long userId,String username,String nickname,String role){}
    public record Access(boolean canWrite,boolean canManage){}
    public record Request(@NotBlank @Size(max=50) String username,@NotNull Role role){}
    @GetMapping("/permissions") public Result<Access> access(@PathVariable Long id){
        var user=CurrentUser.require();var base=permissions.requireKnowledgeBaseAccess(id,user,false);
        return Result.success(new Access(permissions.canWrite(base,user),permissions.canManage(base,user)));
    }
    @GetMapping("/members") public Result<List<Member>> list(@PathVariable Long id){
        permissions.requireKnowledgeBaseManagement(id,CurrentUser.require());
        return Result.success(jdbc.query("SELECT u.id,u.username,u.nickname,m.role FROM knowledge_member m JOIN sys_user u ON u.id=m.user_id WHERE m.knowledge_base_id=? ORDER BY u.username",(rs,row)->new Member(rs.getLong(1),rs.getString(2),rs.getString(3),rs.getString(4)),id));
    }
    @Transactional @PutMapping("/members") public Result<Void> save(@PathVariable Long id,@Valid @RequestBody Request body){
        var base=permissions.requireKnowledgeBaseManagement(id,CurrentUser.require());
        jdbc.queryForList("SELECT id FROM knowledge_base WHERE id=? FOR UPDATE",id);
        permissions.requireKnowledgeBaseManagement(id,CurrentUser.require());
        var found=jdbc.query("SELECT id,role FROM sys_user WHERE username=? AND status=1",(rs,row)->new Member(rs.getLong(1),body.username(),null,rs.getString(2)),body.username().trim());
        if(found.isEmpty())throw new BusinessException(ErrorCode.NOT_FOUND,"找不到已启用的账号");
        var member=found.getFirst();
        if(member.userId()==base.getOwnerId()||"ADMIN".equalsIgnoreCase(member.role()))throw new BusinessException(ErrorCode.BAD_REQUEST,"所有者和系统管理员无需添加成员权限");
        int updated=jdbc.update("UPDATE knowledge_member SET role=? WHERE knowledge_base_id=? AND user_id=?",body.role().name(),id,member.userId());
        if(updated==0)jdbc.update("INSERT INTO knowledge_member(knowledge_base_id,user_id,role) VALUES (?,?,?)",id,member.userId(),body.role().name());
        return Result.success(null);
    }
    @Transactional @DeleteMapping("/members/{userId}") public Result<Void> remove(@PathVariable Long id,@PathVariable long userId){
        permissions.requireKnowledgeBaseManagement(id,CurrentUser.require());
        jdbc.queryForList("SELECT id FROM knowledge_base WHERE id=? FOR UPDATE",id);
        permissions.requireKnowledgeBaseManagement(id,CurrentUser.require());
        jdbc.update("DELETE FROM knowledge_member WHERE knowledge_base_id=? AND user_id=?",id,userId);return Result.success(null);
    }
}
