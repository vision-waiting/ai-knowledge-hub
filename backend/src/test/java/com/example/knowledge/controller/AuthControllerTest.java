package com.example.knowledge.controller;

import com.example.knowledge.service.AuthService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AuthControllerTest {
    @Test
    void loginDelegatesToAuthService() {
        AuthService service = org.mockito.Mockito.mock(AuthService.class);
        AuthController controller = new AuthController(service);
        var response = controller.login(new com.example.knowledge.dto.LoginRequest("student", "secret"));
        assertEquals(200, response.getBody().code());
    }
}

