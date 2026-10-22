package com.fixedsource.service;

import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AuthService {

    public record User(String username, String name, String role, String roleLabel) {}

    private final Map<String, User> users = new LinkedHashMap<>();
    private final Map<String, User> tokens = new ConcurrentHashMap<>();

    public AuthService() {
        users.put("analyst", new User("analyst", "陈分析", "analyst", "监测分析人员"));
        users.put("reviewer", new User("reviewer", "周审核", "reviewer", "审核人员"));
        users.put("inspector", new User("inspector", "李现场", "inspector", "现场核查人员"));
        users.put("admin", new User("admin", "系统管理员", "admin", "系统管理员"));
        users.put("datahub", new User("datahub", "国家数据仓服务", "system", "数据交换服务"));
    }

    public Map<String, Object> login(String username, String password) {
        User user = users.get(username);
        if (user == null || !"123456".equals(password)) {
            return null;
        }
        String token = UUID.randomUUID().toString().replace("-", "");
        tokens.put(token, user);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("token", token);
        result.put("username", user.username());
        result.put("name", user.name());
        result.put("role", user.role());
        result.put("roleLabel", user.roleLabel());
        return result;
    }

    public User findByToken(String token) {
        return token == null ? null : tokens.get(token);
    }

    public boolean hasAnyRole(User user, String... roles) {
        if (user == null) {
            return false;
        }
        if ("admin".equals(user.role())) {
            return true;
        }
        for (String role : roles) {
            if (role.equals(user.role())) {
                return true;
            }
        }
        return false;
    }
}
