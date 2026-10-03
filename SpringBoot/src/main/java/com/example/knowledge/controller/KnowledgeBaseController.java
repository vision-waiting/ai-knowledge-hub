package com.example.knowledge.controller;

import com.example.knowledge.common.Result;
import com.example.knowledge.dto.KnowledgeBaseRequest;
import com.example.knowledge.security.CurrentUser;
import com.example.knowledge.security.JwtPrincipal;
import com.example.knowledge.service.KnowledgeBaseService;
import com.example.knowledge.vo.KnowledgeBaseVO;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/knowledge-bases")
public class KnowledgeBaseController {
    private final KnowledgeBaseService service;

    public KnowledgeBaseController(KnowledgeBaseService service) { this.service = service; }

    @GetMapping
    public ResponseEntity<Result<List<KnowledgeBaseVO>>> list(@RequestParam(required = false) String keyword) {
        return ResponseEntity.ok(Result.success(service.list(CurrentUser.require(), keyword)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Result<KnowledgeBaseVO>> get(@PathVariable Long id) {
        return ResponseEntity.ok(Result.success(service.get(id, CurrentUser.require())));
    }

    @PostMapping
    public ResponseEntity<Result<KnowledgeBaseVO>> create(@Valid @RequestBody KnowledgeBaseRequest request) {
        return ResponseEntity.ok(Result.success(service.create(request, CurrentUser.require())));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Result<KnowledgeBaseVO>> update(@PathVariable Long id, @Valid @RequestBody KnowledgeBaseRequest request) {
        return ResponseEntity.ok(Result.success(service.update(id, request, CurrentUser.require())));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Result<Void>> delete(@PathVariable Long id) {
        service.delete(id, CurrentUser.require());
        return ResponseEntity.ok(Result.success());
    }
}

