package com.fixedsource.controller;

import com.fixedsource.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/auth/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody Map<String, Object> body) {
        String username = String.valueOf(body.getOrDefault("username", ""));
        String password = String.valueOf(body.getOrDefault("password", ""));
        Map<String, Object> result = authService.login(username, password);
        if (result == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "用户名或密码错误"));
        }
        return ResponseEntity.ok(result);
    }

    @PostMapping("/mobile/auth/login")
    public ResponseEntity<Map<String, Object>> mobileLogin(@RequestBody Map<String, Object> body) {
        return login(body);
    }

    @GetMapping("/auth/me")
    public Map<String, Object> me(HttpServletRequest request) {
        AuthService.User user = (AuthService.User) request.getAttribute("user");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("username", user.username());
        result.put("name", user.name());
        result.put("role", user.role());
        result.put("roleLabel", user.roleLabel());
        return result;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of("status", "UP", "time", LocalDateTime.now().toString(), "service", "fixed-source-platform");
    }
}
