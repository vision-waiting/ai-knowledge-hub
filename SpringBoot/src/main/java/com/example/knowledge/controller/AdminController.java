package com.example.knowledge.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.knowledge.common.Result;
import com.example.knowledge.entity.OperationLog;
import com.example.knowledge.entity.SysUser;
import com.example.knowledge.mapper.OperationLogMapper;
import com.example.knowledge.mapper.SysUserMapper;
import com.example.knowledge.service.DashboardService;
import com.example.knowledge.vo.DashboardOverviewVO;
import com.example.knowledge.vo.UserVO;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import jakarta.validation.Valid;
import com.example.knowledge.dto.UserStatusRequest;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private final com.example.knowledge.service.UserService users;
    private final com.example.knowledge.service.OperationLogService logs;
    private final DashboardService dashboardService;
    public AdminController(com.example.knowledge.service.UserService users, com.example.knowledge.service.OperationLogService logs, DashboardService dashboardService) {
        this.users = users; this.logs = logs; this.dashboardService = dashboardService;
    }

    @GetMapping("/users")
    public ResponseEntity<Result<List<UserVO>>> users() { return ResponseEntity.ok(Result.success(users.list())); }

    @GetMapping("/users/{id}")
    public Result<UserVO> user(@PathVariable Long id) { return Result.success(users.get(id)); }

    @PutMapping("/users/{id}")
    public Result<UserVO> updateUser(@PathVariable Long id, @Valid @RequestBody com.example.knowledge.dto.UserUpdateRequest request) {
        return Result.success(users.update(id, request, com.example.knowledge.security.CurrentUser.require()));
    }

    @GetMapping("/logs")
    public ResponseEntity<Result<List<OperationLog>>> logs() { return ResponseEntity.ok(Result.success(logs.recent())); }

    @PutMapping("/users/{id}/status")
    public ResponseEntity<Result<Void>> updateStatus(@PathVariable Long id, @Valid @RequestBody UserStatusRequest request) {
        users.status(id, request.status(), com.example.knowledge.security.CurrentUser.require());
        return ResponseEntity.ok(Result.success());
    }

    @org.springframework.web.bind.annotation.PostMapping("/users")
    public Result<UserVO> create(@Valid @RequestBody com.example.knowledge.dto.UserCreateRequest request) { return Result.success(users.create(request)); }

    @PutMapping("/users/{id}/role")
    public Result<Void> role(@PathVariable Long id, @Valid @RequestBody com.example.knowledge.dto.UserRoleRequest request) {
        users.role(id, request.role(), com.example.knowledge.security.CurrentUser.require()); return Result.success();
    }

    @GetMapping("/statistics")
    public ResponseEntity<Result<DashboardOverviewVO>> statistics() { return ResponseEntity.ok(Result.success(dashboardService.overview())); }
}
