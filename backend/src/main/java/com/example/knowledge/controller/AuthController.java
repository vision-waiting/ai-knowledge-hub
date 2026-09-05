package com.example.knowledge.controller;

import com.example.knowledge.common.Result;
import com.example.knowledge.dto.LoginRequest;
import com.example.knowledge.security.CurrentUser;
import com.example.knowledge.service.AuthService;
import com.example.knowledge.vo.LoginVO;
import com.example.knowledge.vo.UserVO;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) { this.authService = authService; }

    @PostMapping("/login")
    public ResponseEntity<Result<LoginVO>> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(Result.success(authService.login(request.username(), request.password())));
    }

    @GetMapping("/me")
    public ResponseEntity<Result<UserVO>> me() {
        return ResponseEntity.ok(Result.success(authService.getUser(CurrentUser.require().userId())));
    }

    @PostMapping("/logout")
    public ResponseEntity<Result<Void>> logout() {
        return ResponseEntity.ok(Result.success());
    }
}

