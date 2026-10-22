package com.fixedsource.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fixedsource.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Map;

@Component
public class AuthInterceptor implements HandlerInterceptor {

    private final AuthService authService;
    private final ObjectMapper objectMapper;

    public AuthInterceptor(AuthService authService, ObjectMapper objectMapper) {
        this.authService = authService;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }
        String path = request.getRequestURI();
        if (path.startsWith("/mock-source/") || path.startsWith("/h2-console")
                || path.startsWith("/uploads/") || path.equals("/error")
                || path.equals("/api/auth/login") || path.equals("/api/mobile/auth/login")
                || path.equals("/api/health")) {
            return true;
        }
        if (!path.startsWith("/api/")) {
            return true;
        }
        String token = request.getHeader("X-Token");
        if (token == null || token.isBlank()) {
            String authorization = request.getHeader("Authorization");
            if (authorization != null && authorization.startsWith("Bearer ")) {
                token = authorization.substring(7);
            }
        }
        AuthService.User user = authService.findByToken(token);
        if (user == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(objectMapper.writeValueAsString(Map.of("message", "登录已失效，请重新登录")));
            return false;
        }
        request.setAttribute("user", user);
        return true;
    }
}
