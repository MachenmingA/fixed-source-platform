package com.fixedsource.controller;

import com.fixedsource.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
public class RuleController {

    private final JdbcTemplate jdbc;
    private final AuthService authService;

    public RuleController(JdbcTemplate jdbc, AuthService authService) {
        this.jdbc = jdbc;
        this.authService = authService;
    }

    @GetMapping("/api/rules")
    public List<Map<String, Object>> rules() {
        return jdbc.queryForList("""
                SELECT rule_code AS ruleCode, name, category, granularity, window_hours AS windowHours,
                       threshold_expr AS thresholdExpr, enabled, version, description
                FROM rule_config ORDER BY rule_code
                """);
    }

    @PutMapping("/api/rules/{code}")
    public Map<String, Object> update(@PathVariable String code, @RequestBody Map<String, Object> body,
                                      HttpServletRequest request) {
        AuthService.User user = (AuthService.User) request.getAttribute("user");
        if (!authService.hasAnyRole(user, "admin")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只有管理员可以修改规则");
        }
        Object enabledValue = body.get("enabled");
        Boolean enabled = enabledValue == null ? null : Boolean.parseBoolean(String.valueOf(enabledValue));
        Integer windowHours = body.get("windowHours") == null ? null : Integer.parseInt(String.valueOf(body.get("windowHours")));
        String thresholdExpr = body.get("thresholdExpr") == null ? null : String.valueOf(body.get("thresholdExpr"));
        String version = body.get("version") == null ? null : String.valueOf(body.get("version"));
        String description = body.get("description") == null ? null : String.valueOf(body.get("description"));
        jdbc.update("""
                UPDATE rule_config SET enabled=COALESCE(?,enabled), window_hours=COALESCE(?,window_hours),
                    threshold_expr=COALESCE(?,threshold_expr), version=COALESCE(?,version),
                    description=COALESCE(?,description) WHERE rule_code=?
                """, enabled, windowHours, thresholdExpr, version, description, code);
        jdbc.update("INSERT INTO audit_log(action,target_type,target_id,operator,detail) VALUES (?,?,?,?,?)",
                "UPDATE_RULE", "RULE", code, user.name(), "更新规则配置");
        return jdbc.queryForList("""
                SELECT rule_code AS ruleCode, name, category, granularity, window_hours AS windowHours,
                       threshold_expr AS thresholdExpr, enabled, version, description
                FROM rule_config WHERE rule_code=?
                """, code).stream().findFirst().orElse(Map.of());
    }
}
