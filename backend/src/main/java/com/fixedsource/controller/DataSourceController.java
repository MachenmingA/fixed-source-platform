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
@RequestMapping("/api/data-sources")
public class DataSourceController {

    private final JdbcTemplate jdbc;
    private final AuthService authService;

    public DataSourceController(JdbcTemplate jdbc, AuthService authService) {
        this.jdbc = jdbc;
        this.authService = authService;
    }

    @GetMapping
    public List<Map<String, Object>> list() {
        return jdbc.queryForList("""
                SELECT id, name, source_type AS sourceType, base_url AS baseUrl, auth_type AS authType,
                       enabled, version, description,
                       FORMATDATETIME(updated_at,'yyyy-MM-dd HH:mm') AS updatedAt
                FROM data_source_config ORDER BY id
                """);
    }

    @PutMapping("/{id}")
    public Map<String, Object> update(@PathVariable String id, @RequestBody Map<String, Object> body,
                                      HttpServletRequest request) {
        AuthService.User user = (AuthService.User) request.getAttribute("user");
        if (!authService.hasAnyRole(user, "admin")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只有管理员可以修改数据源");
        }
        jdbc.update("""
                UPDATE data_source_config SET name=COALESCE(?,name), base_url=COALESCE(?,base_url),
                    auth_type=COALESCE(?,auth_type), enabled=COALESCE(?,enabled), version=COALESCE(?,version),
                    description=COALESCE(?,description), updated_at=CURRENT_TIMESTAMP
                WHERE id=?
                """, body.get("name"), body.get("baseUrl"), body.get("authType"),
                body.get("enabled") == null ? null : Boolean.parseBoolean(String.valueOf(body.get("enabled"))),
                body.get("version"), body.get("description"), id);
        jdbc.update("INSERT INTO audit_log(action,target_type,target_id,operator,detail) VALUES (?,?,?,?,?)",
                "UPDATE_DATASOURCE", "DATASOURCE", id, user.name(), "更新数据源配置");
        return list().stream().filter(x -> id.equals(String.valueOf(x.get("id")))).findFirst().orElse(Map.of());
    }
}
