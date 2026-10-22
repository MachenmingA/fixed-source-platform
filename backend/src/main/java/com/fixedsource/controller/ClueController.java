package com.fixedsource.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fixedsource.service.AiService;
import com.fixedsource.service.AuthService;
import com.fixedsource.service.EventRiskService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@RestController
public class ClueController {

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final AiService aiService;
    private final EventRiskService eventRiskService;
    private final AuthService authService;

    public ClueController(JdbcTemplate jdbc, ObjectMapper objectMapper, AiService aiService,
                          EventRiskService eventRiskService, AuthService authService) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.aiService = aiService;
        this.eventRiskService = eventRiskService;
        this.authService = authService;
    }

    @GetMapping("/api/clues")
    public List<Map<String, Object>> list(@RequestParam(required = false) String status,
                                          @RequestParam(required = false) String enterpriseId,
                                          @RequestParam(required = false) String ruleCode,
                                          @RequestParam(required = false) String severity,
                                          @RequestParam(required = false) String keyword,
                                          @RequestParam(defaultValue = "100") int limit) {
        StringBuilder sql = new StringBuilder("""
                SELECT c.id, c.clue_no AS clueNo, c.rule_code AS ruleCode, c.rule_name AS ruleName,
                       c.severity, c.status, c.trigger_value AS triggerValue, c.threshold_value AS thresholdValue,
                       c.credibility, c.ai_status AS aiStatus, c.source, c.event_id AS eventId,
                       FORMATDATETIME(c.start_time,'yyyy-MM-dd HH:mm') AS startTime,
                       FORMATDATETIME(c.end_time,'yyyy-MM-dd HH:mm') AS endTime,
                       e.id AS enterpriseId, e.name AS enterpriseName, e.region,
                       p.point_name AS pointName, po.name AS pollutantName, po.code AS pollutantCode, po.unit
                FROM clue c
                JOIN enterprise e ON e.id=c.enterprise_id
                JOIN monitor_point p ON p.id=c.point_id
                JOIN pollutant po ON po.code=c.pollutant_code
                WHERE 1=1
                """);
        List<Object> args = new ArrayList<>();
        if (status != null && !status.isBlank()) { sql.append(" AND c.status=?"); args.add(status); }
        if (enterpriseId != null && !enterpriseId.isBlank()) { sql.append(" AND c.enterprise_id=?"); args.add(enterpriseId); }
        if (ruleCode != null && !ruleCode.isBlank()) { sql.append(" AND c.rule_code=?"); args.add(ruleCode); }
        if (severity != null && !severity.isBlank()) { sql.append(" AND c.severity=?"); args.add(severity); }
        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND (e.name LIKE ? OR po.name LIKE ? OR c.rule_name LIKE ?)");
            args.add("%" + keyword + "%"); args.add("%" + keyword + "%"); args.add("%" + keyword + "%");
        }
        sql.append(" ORDER BY CASE c.severity WHEN 'HIGH' THEN 1 WHEN 'MEDIUM' THEN 2 ELSE 3 END, c.start_time DESC LIMIT ?");
        args.add(Math.min(300, Math.max(1, limit)));
        return jdbc.queryForList(sql.toString(), args.toArray());
    }

    @GetMapping("/api/clues/{id}")
    public Map<String, Object> detail(@PathVariable long id) {
        return buildDetail(id);
    }

    @PostMapping("/api/clues/{id}/confirm")
    public Map<String, Object> confirm(@PathVariable long id, @RequestBody(required = false) Map<String, Object> body,
                                       HttpServletRequest request) {
        AuthService.User user = (AuthService.User) request.getAttribute("user");
        if (!authService.hasAnyRole(user, "reviewer")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只有审核人员可以确认或撤销线索");
        }
        String status = body == null ? "CONFIRMED" : String.valueOf(body.getOrDefault("status", "CONFIRMED"));
        if (!List.of("CONFIRMED", "REVOKED").contains(status)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不支持的确认状态");
        }
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT event_id AS eventId FROM clue WHERE id=?", id);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "线索不存在");
        String note = body == null ? null : String.valueOf(body.getOrDefault("note", ""));
        jdbc.update("UPDATE clue SET status=?, confirmed_by=?, confirmed_at=? WHERE id=?",
                status, user.name(), Timestamp.valueOf(LocalDateTime.now()), id);
        jdbc.update("INSERT INTO audit_log(action,target_type,target_id,operator,detail) VALUES (?,?,?,?,?)",
                "CONFIRM_CLUE", "CLUE", String.valueOf(id), user.name(), status + " " + (note == null ? "" : note));
        Object eventId = rows.get(0).get("eventId");
        if (eventId != null) eventRiskService.refreshEventStatus(((Number) eventId).longValue());
        return buildDetail(id);
    }

    @PostMapping("/api/clues/{id}/ai-assessment")
    public Map<String, Object> ai(@PathVariable long id) {
        Map<String, Object> assessment = aiService.assessClue(id);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("assessment", assessment);
        result.put("credibility", jdbc.queryForObject("SELECT credibility FROM clue WHERE id=?", Integer.class, id));
        return result;
    }

    @GetMapping("/api/clues/{id}/credibility")
    public Map<String, Object> credibility(@PathVariable long id) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT credibility, credibility_json AS credibilityJson, ai_status AS aiStatus
                FROM clue WHERE id=?
                """, id);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "线索不存在");
        Map<String, Object> result = rows.get(0);
        try {
            Object raw = result.remove("credibilityJson");
            result.put("factors", raw == null ? Map.of() : objectMapper.readValue(String.valueOf(raw), new TypeReference<>() {}));
        } catch (Exception ignored) {
            result.put("factors", Map.of());
        }
        result.put("ai", aiService.latestForClue(id));
        return result;
    }

    @PostMapping("/api/clues/manual")
    public Map<String, Object> manual(@RequestBody Map<String, Object> body) {
        String enterpriseId = String.valueOf(body.get("enterpriseId"));
        String pointId = String.valueOf(body.get("pointId"));
        String pollutantCode = String.valueOf(body.get("pollutantCode"));
        String description = String.valueOf(body.getOrDefault("description", "人工标记线索"));
        List<Map<String, Object>> pointRows = jdbc.queryForList("SELECT * FROM monitor_point WHERE id=? AND enterprise_id=?", pointId, enterpriseId);
        if (pointRows.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "企业或监测点不匹配");
        LocalDateTime start = LocalDateTime.now().minusHours(1);
        LocalDateTime end = LocalDateTime.now();
        if (body.get("startTime") != null) start = LocalDateTime.parse(String.valueOf(body.get("startTime")), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        if (body.get("endTime") != null) end = LocalDateTime.parse(String.valueOf(body.get("endTime")), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        String clueNo = "CLM" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + String.format("%03d", ThreadLocalRandom.current().nextInt(1000));
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("ruleCode", "MANUAL");
        evidence.put("ruleName", "人工标记");
        evidence.put("summary", description);
        evidence.put("values", List.of(description));
        try {
            jdbc.update("""
                    INSERT INTO clue(clue_no,enterprise_id,point_id,pollutant_code,rule_code,rule_name,severity,
                        start_time,end_time,trigger_value,threshold_value,evidence_json,fingerprint,status,source,
                        credibility,credibility_json,ai_status)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                    """, clueNo, enterpriseId, pointId, pollutantCode, "MANUAL", "人工标记", "LOW",
                    Timestamp.valueOf(start), Timestamp.valueOf(end), 0, 0,
                    objectMapper.writeValueAsString(evidence), "MANUAL|" + clueNo,
                    "PENDING", "MANUAL", 65, "{}", "NONE");
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "创建人工线索失败");
        }
        Long id = jdbc.queryForObject("SELECT id FROM clue WHERE clue_no=?", Long.class, clueNo);
        aiService.assessClue(id);
        eventRiskService.rebuildForDate(start.toLocalDate());
        return buildDetail(id);
    }

    private Map<String, Object> buildDetail(long id) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT c.id, c.clue_no AS clueNo, c.enterprise_id AS enterpriseId, c.point_id AS pointId,
                       c.pollutant_code AS pollutantCode, c.rule_code AS ruleCode, c.rule_name AS ruleName,
                       c.severity, c.status, c.trigger_value AS triggerValue, c.threshold_value AS thresholdValue,
                       c.credibility, c.ai_status AS aiStatus, c.source, c.event_id AS eventId,
                       c.evidence_json AS evidenceJson, c.credibility_json AS credibilityJson,
                       FORMATDATETIME(c.start_time,'yyyy-MM-dd HH:mm') AS startTime,
                       FORMATDATETIME(c.end_time,'yyyy-MM-dd HH:mm') AS endTime,
                       FORMATDATETIME(c.created_at,'yyyy-MM-dd HH:mm:ss') AS createdAt,
                       e.name AS enterpriseName, e.industry, e.region, e.risk_level AS enterpriseRiskLevel,
                       p.point_name AS pointName, p.outlet_name AS outletName,
                       po.name AS pollutantName, po.unit, po.limit_value AS limitValue, po.standard_ref AS standardRef
                FROM clue c
                JOIN enterprise e ON e.id=c.enterprise_id
                JOIN monitor_point p ON p.id=c.point_id
                JOIN pollutant po ON po.code=c.pollutant_code
                WHERE c.id=?
                """, id);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "线索不存在");
        Map<String, Object> clue = rows.get(0);
        try {
            Object raw = clue.remove("evidenceJson");
            clue.put("evidence", raw == null ? Map.of() : objectMapper.readValue(String.valueOf(raw), new TypeReference<>() {}));
        } catch (Exception ignored) {
            clue.put("evidence", Map.of());
        }
        try {
            Object raw = clue.remove("credibilityJson");
            clue.put("credibilityFactors", raw == null ? Map.of() : objectMapper.readValue(String.valueOf(raw), new TypeReference<>() {}));
        } catch (Exception ignored) {
            clue.put("credibilityFactors", Map.of());
        }
        clue.put("ai", aiService.latestForClue(id));
        List<Map<String, Object>> tasks = jdbc.queryForList("""
                SELECT id, task_no AS taskNo, status, assignee, external_no AS externalNo,
                       FORMATDATETIME(due_at,'yyyy-MM-dd HH:mm') AS dueAt,
                       FORMATDATETIME(created_at,'yyyy-MM-dd HH:mm') AS createdAt
                FROM inspection_task WHERE clue_id=? ORDER BY id DESC LIMIT 1
                """, id);
        clue.put("task", tasks.isEmpty() ? null : tasks.get(0));
        return clue;
    }
}
