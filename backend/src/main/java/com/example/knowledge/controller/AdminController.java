package com.example.knowledge.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.knowledge.common.Result;
import com.example.knowledge.entity.OperationLog;
import com.example.knowledge.mapper.OperationLogMapper;
import com.example.knowledge.mapper.SysUserMapper;
import com.example.knowledge.service.DashboardService;
import com.example.knowledge.vo.DashboardOverviewVO;
import com.example.knowledge.vo.UserVO;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private final SysUserMapper userMapper; private final OperationLogMapper logMapper; private final DashboardService dashboardService;
    public AdminController(SysUserMapper userMapper, OperationLogMapper logMapper, DashboardService dashboardService) { this.userMapper = userMapper; this.logMapper = logMapper; this.dashboardService = dashboardService; }

    @GetMapping("/users")
    public ResponseEntity<Result<List<UserVO>>> users() { return ResponseEntity.ok(Result.success(userMapper.selectList(Wrappers.emptyWrapper()).stream().map(user -> new UserVO(user.getId(), user.getUsername(), user.getNickname(), user.getRole())).toList())); }

    @GetMapping("/logs")
    public ResponseEntity<Result<List<OperationLog>>> logs() { return ResponseEntity.ok(Result.success(logMapper.selectList(Wrappers.<OperationLog>query().orderByDesc("created_at").last("limit 100")))); }

    @GetMapping("/statistics")
    public ResponseEntity<Result<DashboardOverviewVO>> statistics() { return ResponseEntity.ok(Result.success(dashboardService.overview())); }
}

