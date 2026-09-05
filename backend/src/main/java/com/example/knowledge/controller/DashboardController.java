package com.example.knowledge.controller;

import com.example.knowledge.common.Result;
import com.example.knowledge.service.DashboardService;
import com.example.knowledge.vo.DashboardOverviewVO;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {
    private final DashboardService service;

    public DashboardController(DashboardService service) { this.service = service; }

    @GetMapping("/overview")
    public ResponseEntity<Result<DashboardOverviewVO>> overview() {
        return ResponseEntity.ok(Result.success(service.overview()));
    }
}

