package com.fixedsource.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fixedsource.service.AuthService;
import com.fixedsource.service.EventRiskService;
import com.fixedsource.service.TaskService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
public class EnterpriseController {

    private static final Set<String> PRODUCTION_STATUS = Set.of("NORMAL", "LIMITED", "STOPPED", "MAINTENANCE");
    private static final Set<String> ENTERPRISE_STATUS = Set.of("ACTIVE", "INACTIVE");
    private static final Set<String> POINT_STATUS = Set.of("NORMAL", "MAINTENANCE", "STOPPED");
    private static final Set<String> POINT_TYPES = Set.of("废气", "废水", "无组织", "其他");

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final EventRiskService eventRiskService;
    private final TaskService taskService;
    private final AuthService authService;

    public EnterpriseController(JdbcTemplate jdbc, ObjectMapper objectMapper, EventRiskService eventRiskService,
                                TaskService taskService, AuthService authService) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.eventRiskService = eventRiskService;
        this.taskService = taskService;
        this.authService = authService;
    }

    @GetMapping("/api/enterprises")
    public List<Map<String, Object>> list(@RequestParam(required = false) String keyword,
                                          @RequestParam(required = false) String region,
                                          @RequestParam(required = false) String riskLevel,
                                          @RequestParam(required = false) String status) {
        StringBuilder sql = new StringBuilder("""
                SELECT e.id, e.name, e.industry, e.region, e.management_level AS managementLevel,
                       e.credit_code AS creditCode, e.risk_score AS riskScore, e.risk_level AS riskLevel,
                       e.address, e.legal_representative AS legalRepresentative, e.contact_name AS contactName,
                       e.contact_phone AS contactPhone, e.longitude, e.latitude,
                       e.production_status AS productionStatus, e.permit_no AS permitNo, e.status, e.remark,
                       FORMATDATETIME(e.updated_at,'yyyy-MM-dd HH:mm') AS updatedAt,
                       (SELECT COUNT(*) FROM monitor_point p WHERE p.enterprise_id=e.id) AS pointCount,
                       (SELECT COUNT(*) FROM abnormal_event ev WHERE ev.enterprise_id=e.id AND ev.status <> 'CLOSED') AS pendingEvents
                FROM enterprise e WHERE 1=1
                """);
        List<Object> args = new ArrayList<>();
        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND (e.name LIKE ? OR e.industry LIKE ? OR e.credit_code LIKE ?)");
            args.add("%" + keyword + "%");
            args.add("%" + keyword + "%");
            args.add("%" + keyword + "%");
        }
        if (region != null && !region.isBlank()) {
            sql.append(" AND e.region=?");
            args.add(region);
        }
        if (riskLevel != null && !riskLevel.isBlank()) {
            sql.append(" AND e.risk_level=?");
            args.add(riskLevel);
        }
        if (status != null && !status.isBlank()) {
            sql.append(" AND e.status=?");
            args.add(status);
        }
        sql.append(" ORDER BY CASE WHEN e.status='ACTIVE' THEN 0 ELSE 1 END, e.risk_score DESC, e.name");
        return jdbc.queryForList(sql.toString(), args.toArray());
    }

    @PostMapping("/api/enterprises")
    public Map<String, Object> create(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        AuthService.User user = requireMaintainer(request);
        String name = requireText(text(body, "name", null), "企业名称");
        String creditCode = requireText(text(body, "creditCode", null), "统一社会信用代码");
        String region = requireText(text(body, "region", null), "所属区域");
        String industry = text(body, "industry", "其他");
        if (industry == null || industry.isBlank()) industry = "其他";
        String managementLevel = normalizeChoice(text(body, "managementLevel", "一般"), Set.of("重点", "一般"), "管理类别");
        String productionStatus = normalizeChoice(text(body, "productionStatus", "NORMAL"), PRODUCTION_STATUS, "生产状态");
        String status = normalizeChoice(text(body, "status", "ACTIVE"), ENTERPRISE_STATUS, "企业状态");
        Double longitude = number(body, "longitude", null);
        Double latitude = number(body, "latitude", null);
        validateCoordinate(longitude, latitude);
        assertUniqueEnterprise(name, creditCode, null);

        String id = nextEnterpriseId();
        jdbc.update("""
                INSERT INTO enterprise(id,name,industry,region,management_level,credit_code,risk_score,risk_level,
                    address,legal_representative,contact_name,contact_phone,longitude,latitude,production_status,
                    permit_no,status,remark,updated_at)
                VALUES (?,?,?,?,?,?,0,'A',?,?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP)
                """, id, name, industry, region, managementLevel, creditCode,
                text(body, "address", null), text(body, "legalRepresentative", null),
                text(body, "contactName", null), text(body, "contactPhone", null),
                longitude, latitude, productionStatus, text(body, "permitNo", null), status,
                text(body, "remark", null));
        audit(user, "CREATE_ENTERPRISE", id, "新增企业台账：" + name);
        return enterpriseRow(id);
    }

    @PutMapping("/api/enterprises/{id}")
    public Map<String, Object> update(@PathVariable String id, @RequestBody Map<String, Object> body,
                                      HttpServletRequest request) {
        AuthService.User user = requireMaintainer(request);
        Map<String, Object> current = enterpriseRow(id);
        if (current == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "企业不存在");
        String name = requireText(text(body, "name", String.valueOf(current.get("name"))), "企业名称");
        String creditCode = text(body, "creditCode", stringValue(current.get("creditCode")));
        if (creditCode == null || creditCode.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "统一社会信用代码不能为空");
        }
        assertUniqueEnterprise(name, creditCode, id);
        String region = requireText(text(body, "region", stringValue(current.get("region"))), "所属区域");
        String industry = text(body, "industry", stringValue(current.get("industry")));
        String managementLevel = normalizeChoice(
                text(body, "managementLevel", stringValue(current.get("managementLevel"))),
                Set.of("重点", "一般"), "管理类别");
        String productionStatus = normalizeChoice(
                text(body, "productionStatus", stringValue(current.get("productionStatus"))),
                PRODUCTION_STATUS, "生产状态");
        String status = normalizeChoice(
                text(body, "status", stringValue(current.get("status"))),
                ENTERPRISE_STATUS, "企业状态");
        Double longitude = number(body, "longitude", doubleValue(current.get("longitude")));
        Double latitude = number(body, "latitude", doubleValue(current.get("latitude")));
        validateCoordinate(longitude, latitude);
        jdbc.update("""
                UPDATE enterprise SET name=?, industry=?, region=?, management_level=?, credit_code=?,
                    address=?, legal_representative=?, contact_name=?, contact_phone=?, longitude=?, latitude=?,
                    production_status=?, permit_no=?, status=?, remark=?, updated_at=CURRENT_TIMESTAMP
                WHERE id=?
                """, name, industry == null ? "其他" : industry, region, managementLevel, creditCode,
                text(body, "address", stringValue(current.get("address"))),
                text(body, "legalRepresentative", stringValue(current.get("legalRepresentative"))),
                text(body, "contactName", stringValue(current.get("contactName"))),
                text(body, "contactPhone", stringValue(current.get("contactPhone"))),
                longitude, latitude, productionStatus, text(body, "permitNo", stringValue(current.get("permitNo"))),
                status, text(body, "remark", stringValue(current.get("remark"))), id);
        audit(user, "UPDATE_ENTERPRISE", id, "维护企业台账：" + name);
        return enterpriseRow(id);
    }

    @PostMapping("/api/enterprises/{id}/points")
    public Map<String, Object> createPoint(@PathVariable String id, @RequestBody Map<String, Object> body,
                                           HttpServletRequest request) {
        AuthService.User user = requireMaintainer(request);
        if (enterpriseRow(id) == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "企业不存在");
        String pointName = requireText(text(body, "pointName", null), "监测点名称");
        String outletName = requireText(text(body, "outletName", null), "排口名称");
        String pointType = normalizeChoice(text(body, "pointType", "废气"), POINT_TYPES, "点位类型");
        String status = normalizeChoice(text(body, "status", "NORMAL"), POINT_STATUS, "点位状态");
        String pointId = nextPointId(id);
        jdbc.update("""
                INSERT INTO monitor_point(id,enterprise_id,outlet_name,point_name,point_type,status,updated_at)
                VALUES (?,?,?,?,?,?,CURRENT_TIMESTAMP)
                """, pointId, id, outletName, pointName, pointType, status);
        audit(user, "CREATE_POINT", pointId, "新增监测点：" + pointName + "（企业 " + id + "）");
        return pointRow(pointId);
    }

    @PutMapping("/api/monitor-points/{pointId}")
    public Map<String, Object> updatePoint(@PathVariable String pointId, @RequestBody Map<String, Object> body,
                                           HttpServletRequest request) {
        AuthService.User user = requireMaintainer(request);
        Map<String, Object> current = pointRow(pointId);
        if (current == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "监测点不存在");
        String pointName = requireText(text(body, "pointName", stringValue(current.get("pointName"))), "监测点名称");
        String outletName = requireText(text(body, "outletName", stringValue(current.get("outletName"))), "排口名称");
        String pointType = normalizeChoice(text(body, "pointType", stringValue(current.get("pointType"))),
                POINT_TYPES, "点位类型");
        String status = normalizeChoice(text(body, "status", stringValue(current.get("status"))),
                POINT_STATUS, "点位状态");
        jdbc.update("""
                UPDATE monitor_point SET outlet_name=?, point_name=?, point_type=?, status=?, updated_at=CURRENT_TIMESTAMP
                WHERE id=?
                """, outletName, pointName, pointType, status, pointId);
        audit(user, "UPDATE_POINT", pointId, "维护监测点：" + pointName);
        return pointRow(pointId);
    }

    @GetMapping("/api/enterprises/{id}/profile")
    public Map<String, Object> profile(@PathVariable String id) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT e.id, e.name, e.industry, e.region, e.management_level AS managementLevel,
                       e.credit_code AS creditCode, e.risk_score AS riskScore, e.risk_level AS riskLevel,
                       e.address, e.legal_representative AS legalRepresentative, e.contact_name AS contactName,
                       e.contact_phone AS contactPhone, e.longitude, e.latitude,
                       e.production_status AS productionStatus, e.permit_no AS permitNo, e.status, e.remark,
                       FORMATDATETIME(e.updated_at,'yyyy-MM-dd HH:mm') AS updatedAt
                FROM enterprise e WHERE e.id=?
                """, id);
        if (rows.isEmpty()) return null;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("enterprise", rows.get(0));
        List<Map<String, Object>> points = jdbc.queryForList("""
                SELECT p.id, p.enterprise_id AS enterpriseId, p.outlet_name AS outletName, p.point_name AS pointName,
                       p.point_type AS pointType, p.status, COUNT(DISTINCT mr.pollutant_code) AS pollutantCount,
                       FORMATDATETIME(MAX(mr.monitor_time),'yyyy-MM-dd HH:mm') AS lastMonitorTime
                FROM monitor_point p LEFT JOIN monitoring_record mr ON mr.point_id=p.id
                WHERE p.enterprise_id=?
                GROUP BY p.id, p.enterprise_id, p.outlet_name, p.point_name, p.point_type, p.status
                ORDER BY p.id
                """, id);
        result.put("points", points);
        List<Map<String, Object>> factors = jdbc.queryForList("""
                SELECT factors_json AS factorsJson, score, level, FORMATDATETIME(created_at,'yyyy-MM-dd HH:mm') AS createdAt
                FROM risk_snapshot WHERE enterprise_id=? ORDER BY id DESC LIMIT 1
                """, id);
        if (!factors.isEmpty()) {
            Map<String, Object> f = factors.get(0);
            try {
                f.put("factors", objectMapper.readValue(String.valueOf(f.remove("factorsJson")), new TypeReference<>() {}));
            } catch (Exception ignored) {
                f.put("factors", Map.of());
            }
            result.put("risk", f);
        } else {
            result.put("risk", Map.of());
        }
        List<Map<String, Object>> events = eventRiskService.listEvents(null, null, id, null);
        result.put("events", events.size() > 12 ? events.subList(0, 12) : events);
        List<Map<String, Object>> tasks = jdbc.queryForList("""
                SELECT t.id, t.task_no AS taskNo, t.title, t.status, t.assignee,
                       FORMATDATETIME(t.due_at,'yyyy-MM-dd HH:mm') AS dueAt,
                       FORMATDATETIME(t.feedback_at,'yyyy-MM-dd HH:mm') AS feedbackAt
                FROM inspection_task t WHERE t.enterprise_id=? ORDER BY t.id DESC LIMIT 10
                """, id);
        result.put("tasks", tasks);
        List<Map<String, Object>> trend = jdbc.queryForList("""
                SELECT FORMATDATETIME(rs.created_at,'yyyy-MM-dd') AS bizDay, rs.score
                FROM risk_snapshot rs WHERE rs.enterprise_id=? ORDER BY rs.id DESC LIMIT 7
                """, id);
        java.util.Collections.reverse(trend);
        result.put("riskTrend", trend);
        List<Map<String, Object>> rules = jdbc.queryForList("""
                SELECT c.rule_code AS ruleCode, c.rule_name AS ruleName, COUNT(*) AS count
                FROM clue c WHERE c.enterprise_id=? GROUP BY c.rule_code, c.rule_name ORDER BY count DESC
                """, id);
        result.put("ruleStats", rules);
        return result;
    }

    @GetMapping("/api/enterprises/{id}/risk")
    public Map<String, Object> risk(@PathVariable String id) {
        Map<String, Object> result = new LinkedHashMap<>();
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT e.id, e.name, e.industry, e.region, e.risk_score AS riskScore, e.risk_level AS riskLevel
                FROM enterprise e WHERE e.id=?
                """, id);
        result.put("enterprise", rows.isEmpty() ? null : rows.get(0));
        List<Map<String, Object>> factors = jdbc.queryForList("""
                SELECT factors_json AS factorsJson, score, level, FORMATDATETIME(created_at,'yyyy-MM-dd HH:mm') AS createdAt
                FROM risk_snapshot WHERE enterprise_id=? ORDER BY id DESC LIMIT 1
                """, id);
        if (!factors.isEmpty()) {
            Map<String, Object> f = factors.get(0);
            try {
                f.put("factors", objectMapper.readValue(String.valueOf(f.remove("factorsJson")), new TypeReference<>() {}));
            } catch (Exception ignored) {
                f.put("factors", Map.of());
            }
            result.put("risk", f);
        }
        result.put("events", eventRiskService.listEvents(null, null, id, null));
        return result;
    }

    @GetMapping("/api/risk/ranking")
    public List<Map<String, Object>> ranking() {
        return eventRiskService.riskRanking();
    }

    @GetMapping("/api/monitoring/trend")
    public Map<String, Object> trend(@RequestParam String enterpriseId,
                                     @RequestParam(required = false) String pointId,
                                     @RequestParam(required = false) String pollutantCode,
                                     @RequestParam(defaultValue = "48") int hours) {
        int h = Math.min(168, Math.max(6, hours));
        List<Map<String, Object>> points = jdbc.queryForList("""
                SELECT id, point_name AS pointName, point_type AS pointType FROM monitor_point
                WHERE enterprise_id=? ORDER BY id
                """, enterpriseId);
        if (points.isEmpty()) return Map.of("points", List.of(), "series", List.of());
        String pid = pointId;
        if (pid == null || pid.isBlank()) pid = String.valueOf(points.get(0).get("id"));
        List<Map<String, Object>> pollutants = jdbc.queryForList("""
                SELECT DISTINCT po.code, po.name, po.unit, po.limit_value AS limitValue, po.standard_ref AS standardRef
                FROM monitoring_record mr JOIN pollutant po ON po.code=mr.pollutant_code
                WHERE mr.enterprise_id=? AND mr.point_id=? ORDER BY po.code
                """, enterpriseId, pid);
        String code = pollutantCode;
        if (code == null || code.isBlank()) {
            code = pollutants.isEmpty() ? "SO2" : String.valueOf(pollutants.get(0).get("code"));
        }
        List<Map<String, Object>> series = jdbc.queryForList("""
                SELECT FORMATDATETIME(monitor_time,'yyyy-MM-dd HH:mm') AS "time", measured_value AS "value",
                       data_status AS "status", converted_value AS convertedValue, flow_value AS flowValue
                FROM monitoring_record WHERE enterprise_id=? AND point_id=? AND pollutant_code=?
                  AND monitor_time >= ? ORDER BY monitor_time
                """, enterpriseId, pid, code, Timestamp.valueOf(LocalDateTime.now().minusHours(h)));
        String finalCode = code;
        Map<String, Object> pollutant = pollutants.stream().filter(m -> finalCode.equals(m.get("code"))).findFirst().orElse(Map.of());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("points", points);
        result.put("pollutants", pollutants);
        result.put("pointId", pid);
        result.put("pollutantCode", code);
        result.put("pollutant", pollutant);
        result.put("series", series);
        return result;
    }

    private Map<String, Object> enterpriseRow(String id) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT e.id, e.name, e.industry, e.region, e.management_level AS managementLevel,
                       e.credit_code AS creditCode, e.risk_score AS riskScore, e.risk_level AS riskLevel,
                       e.address, e.legal_representative AS legalRepresentative, e.contact_name AS contactName,
                       e.contact_phone AS contactPhone, e.longitude, e.latitude,
                       e.production_status AS productionStatus, e.permit_no AS permitNo, e.status, e.remark,
                       FORMATDATETIME(e.updated_at,'yyyy-MM-dd HH:mm') AS updatedAt,
                       (SELECT COUNT(*) FROM monitor_point p WHERE p.enterprise_id=e.id) AS pointCount,
                       (SELECT COUNT(*) FROM abnormal_event ev WHERE ev.enterprise_id=e.id AND ev.status <> 'CLOSED') AS pendingEvents
                FROM enterprise e WHERE e.id=?
                """, id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private Map<String, Object> pointRow(String pointId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT p.id, p.enterprise_id AS enterpriseId, p.outlet_name AS outletName, p.point_name AS pointName,
                       p.point_type AS pointType, p.status, COUNT(DISTINCT mr.pollutant_code) AS pollutantCount,
                       FORMATDATETIME(MAX(mr.monitor_time),'yyyy-MM-dd HH:mm') AS lastMonitorTime,
                       FORMATDATETIME(p.updated_at,'yyyy-MM-dd HH:mm') AS updatedAt
                FROM monitor_point p LEFT JOIN monitoring_record mr ON mr.point_id=p.id
                WHERE p.id=? GROUP BY p.id, p.enterprise_id, p.outlet_name, p.point_name, p.point_type, p.status, p.updated_at
                """, pointId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private AuthService.User requireMaintainer(HttpServletRequest request) {
        AuthService.User user = (AuthService.User) request.getAttribute("user");
        if (!authService.hasAnyRole(user, "reviewer", "admin")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只有审核人员或管理员可以维护企业台账");
        }
        return user;
    }

    private void assertUniqueEnterprise(String name, String creditCode, String excludeId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT id, name, credit_code AS creditCode FROM enterprise
                WHERE (name=? OR credit_code=?) AND (? IS NULL OR id<>?)
                """, name, creditCode, excludeId, excludeId);
        if (!rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "企业名称或统一社会信用代码已存在");
        }
    }

    private String nextEnterpriseId() {
        int max = 0;
        for (String id : jdbc.queryForList("SELECT id FROM enterprise", String.class)) {
            if (id != null && id.matches("E\\d+")) {
                max = Math.max(max, Integer.parseInt(id.substring(1)));
            }
        }
        return "E" + String.format("%03d", max + 1);
    }

    private String nextPointId(String enterpriseId) {
        String digits = enterpriseId.replaceAll("\\D", "");
        String prefix = digits.isBlank()
                ? "P-" + enterpriseId.replaceAll("[^A-Za-z0-9]", "") + "-"
                : "P" + digits + "-";
        int max = 0;
        for (String id : jdbc.queryForList("SELECT id FROM monitor_point WHERE id LIKE ?", String.class, prefix + "%")) {
            if (id == null) continue;
            String suffix = id.substring(prefix.length());
            try {
                max = Math.max(max, Integer.parseInt(suffix));
            } catch (NumberFormatException ignored) {
            }
        }
        return prefix + (max + 1);
    }

    private String text(Map<String, Object> body, String key, String fallback) {
        if (body == null || !body.containsKey(key)) return fallback;
        Object value = body.get(key);
        return value == null ? null : String.valueOf(value).trim();
    }

    private Double number(Map<String, Object> body, String key, Double fallback) {
        if (body == null || !body.containsKey(key)) return fallback;
        Object value = body.get(key);
        if (value == null || String.valueOf(value).isBlank()) return null;
        try {
            return Double.valueOf(String.valueOf(value));
        } catch (NumberFormatException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, key + " 必须是数字");
        }
    }

    private Double doubleValue(Object value) {
        if (value == null) return null;
        try {
            return Double.valueOf(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String requireText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, label + "不能为空");
        }
        return value;
    }

    private String normalizeChoice(String value, Set<String> choices, String label) {
        if (value == null || !choices.contains(value)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, label + "取值不合法");
        }
        return value;
    }

    private void validateCoordinate(Double longitude, Double latitude) {
        if (longitude == null && latitude == null) return;
        if (longitude == null || latitude == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "经纬度需要同时填写");
        }
        if (longitude < 115.0 || longitude > 117.6 || latitude < 39.3 || latitude > 41.2) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "坐标超出北京辖区范围");
        }
    }

    private void audit(AuthService.User user, String action, String targetId, String detail) {
        jdbc.update("INSERT INTO audit_log(action,target_type,target_id,operator,detail) VALUES (?,?,?,?,?)",
                action, "ENTERPRISE", targetId, user == null ? "system" : user.name(), detail);
    }
}
