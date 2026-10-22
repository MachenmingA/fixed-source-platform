package com.fixedsource.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Service
public class EventRiskService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public EventRiskService(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public void rebuildForDate(LocalDate date) {
        Timestamp dayStart = Timestamp.valueOf(date.atStartOfDay());
        Timestamp dayEnd = Timestamp.valueOf(date.plusDays(1).atStartOfDay());
        List<Map<String, Object>> clues = jdbc.queryForList("""
                SELECT id, enterprise_id AS enterpriseId, point_id AS pointId, pollutant_code AS pollutantCode,
                       rule_name AS ruleName, severity, start_time AS startTime, end_time AS endTime,
                       status, credibility, event_id AS eventId
                FROM clue WHERE start_time >= ? AND start_time < ?
                ORDER BY enterprise_id, point_id, pollutant_code, start_time
                """, dayStart, dayEnd);
        for (Map<String, Object> clue : clues) {
            if (clue.get("eventId") != null) continue;
            String enterpriseId = String.valueOf(clue.get("enterpriseId"));
            String pointId = String.valueOf(clue.get("pointId"));
            String pollutantCode = String.valueOf(clue.get("pollutantCode"));
            String eventKey = enterpriseId + "|" + pointId + "|" + pollutantCode + "|" + date;
            List<Map<String, Object>> existing = jdbc.queryForList(
                    "SELECT id FROM abnormal_event WHERE event_key=?", eventKey);
            long eventId;
            if (existing.isEmpty()) {
                MockSourceService.PollutantDef pollutant = MockSourceService.pollutant(pollutantCode);
                String pollutantName = pollutant == null ? pollutantCode : pollutant.name();
                String eventNo = "EV" + date.format(DateTimeFormatter.BASIC_ISO_DATE)
                        + String.format("%03d", ThreadLocalRandom.current().nextInt(1000));
                jdbc.update("""
                        INSERT INTO abnormal_event(event_no,event_key,enterprise_id,point_id,pollutant_code,start_time,end_time,
                            title,clue_count,max_severity,priority,risk_score,credibility,status)
                        VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                        """, eventNo, eventKey, enterpriseId, pointId, pollutantCode,
                        Timestamp.valueOf(date.atStartOfDay()), Timestamp.valueOf(date.atTime(23, 59)),
                        pollutantName + " 待汇总事件", 0, "MEDIUM", "P3", 0, 60, "PENDING");
                eventId = jdbc.queryForObject("SELECT id FROM abnormal_event WHERE event_key=?", Long.class, eventKey);
            } else {
                eventId = ((Number) existing.get(0).get("id")).longValue();
            }
            jdbc.update("UPDATE clue SET event_id=? WHERE id=?", eventId, clue.get("id"));
        }

        List<Map<String, Object>> assigned = jdbc.queryForList("""
                SELECT c.id, c.event_id AS eventId, c.enterprise_id AS enterpriseId, c.point_id AS pointId,
                       c.pollutant_code AS pollutantCode, c.rule_name AS ruleName, c.severity,
                       c.start_time AS startTime, c.end_time AS endTime, c.status, c.credibility,
                       ev.event_key AS eventKey
                FROM clue c JOIN abnormal_event ev ON ev.id=c.event_id
                WHERE c.start_time >= ? AND c.start_time < ? AND c.event_id IS NOT NULL
                ORDER BY c.event_id, c.start_time
                """, dayStart, dayEnd);
        Map<Long, List<Map<String, Object>>> groups = new LinkedHashMap<>();
        for (Map<String, Object> clue : assigned) {
            long eventId = ((Number) clue.get("eventId")).longValue();
            groups.computeIfAbsent(eventId, key -> new ArrayList<>()).add(clue);
        }
        for (Map.Entry<Long, List<Map<String, Object>>> entry : groups.entrySet()) {
            List<Map<String, Object>> list = entry.getValue();
            Map<String, Object> first = list.get(0);
            String enterpriseId = String.valueOf(first.get("enterpriseId"));
            String pointId = String.valueOf(first.get("pointId"));
            String pollutantCode = String.valueOf(first.get("pollutantCode"));
            String maxSeverity = list.stream().map(x -> String.valueOf(x.get("severity")))
                    .max(Comparator.comparingInt(this::severityWeight)).orElse("LOW");
            int credibility = (int) Math.round(list.stream().mapToInt(
                    x -> ((Number) x.get("credibility")).intValue()).average().orElse(60));
            LocalDateTime start = list.stream().map(x -> ((Timestamp) x.get("startTime")).toLocalDateTime())
                    .min(LocalDateTime::compareTo).orElse(date.atStartOfDay());
            LocalDateTime end = list.stream().map(x -> ((Timestamp) x.get("endTime")).toLocalDateTime())
                    .max(LocalDateTime::compareTo).orElse(date.atTime(23, 59));
            List<String> ruleNames = list.stream().map(x -> String.valueOf(x.get("ruleName"))).distinct().limit(3).toList();
            MockSourceService.PollutantDef pollutant = MockSourceService.pollutant(pollutantCode);
            String pollutantName = pollutant == null ? pollutantCode : pollutant.name();
            String title = pollutantName + " " + String.join("、", ruleNames);
            if (list.size() > 1) title += "（" + list.size() + " 条证据）";
            if (String.valueOf(first.get("eventKey")).contains("|split-")) title = "拆分事件：" + title;
            if (title.length() > 200) title = title.substring(0, 200);
            Integer riskScore = jdbc.queryForObject(
                    "SELECT risk_score FROM enterprise WHERE id=?", Integer.class, enterpriseId);
            String priority = "HIGH".equals(maxSeverity) && credibility >= 65 ? "P1"
                    : ("HIGH".equals(maxSeverity) || ("MEDIUM".equals(maxSeverity) && credibility >= 55) ? "P2" : "P3");
            String status = deriveStatus(list);
            jdbc.update("""
                    UPDATE abnormal_event SET start_time=?, end_time=?, title=?, clue_count=?, max_severity=?,
                        priority=?, risk_score=?, credibility=?, status=? WHERE id=?
                    """, Timestamp.valueOf(start), Timestamp.valueOf(end), title, list.size(), maxSeverity,
                    priority, riskScore == null ? 0 : riskScore, credibility, status, entry.getKey());
        }
        List<Long> emptyEvents = jdbc.queryForList("""
                SELECT ev.id FROM abnormal_event ev
                WHERE ev.start_time >= ? AND ev.start_time < ?
                  AND NOT EXISTS (SELECT 1 FROM clue c WHERE c.event_id=ev.id)
                """, Long.class, dayStart, dayEnd);
        for (Long emptyId : emptyEvents) {
            jdbc.update("DELETE FROM abnormal_event WHERE id=?", emptyId);
        }
    }

    private String deriveStatus(List<Map<String, Object>> clues) {
        Set<String> statuses = clues.stream().map(x -> String.valueOf(x.get("status"))).collect(Collectors.toSet());
        if (statuses.stream().allMatch("CLOSED"::equals)) return "CLOSED";
        if (statuses.contains("FEEDBACK")) return "FEEDBACK";
        if (statuses.contains("DISPATCHED") || statuses.contains("IN_PROGRESS")) return "DISPATCHED";
        if (statuses.contains("CONFIRMED")) return "CONFIRMED";
        return "PENDING";
    }

    public void rebuildRiskForAll() {
        List<Map<String, Object>> enterprises = jdbc.queryForList("SELECT id, name, industry, region FROM enterprise");
        LocalDateTime sevenDaysAgo = LocalDateTime.now().minusDays(7);
        for (Map<String, Object> e : enterprises) {
            String enterpriseId = String.valueOf(e.get("id"));
            List<Map<String, Object>> events = jdbc.queryForList("""
                    SELECT max_severity AS maxSeverity, status FROM abnormal_event
                    WHERE enterprise_id=? AND start_time >= ?
                    """, enterpriseId, Timestamp.valueOf(sevenDaysAgo));
            int high = 0, medium = 0, low = 0;
            for (Map<String, Object> ev : events) {
                String severity = String.valueOf(ev.get("maxSeverity"));
                if ("HIGH".equals(severity)) high++;
                else if ("MEDIUM".equals(severity)) medium++;
                else low++;
            }
            int pendingTasks = n("SELECT COUNT(*) FROM inspection_task WHERE enterprise_id=? AND status NOT IN ('CLOSED')", enterpriseId);
            int feedbacks = n("SELECT COUNT(*) FROM task_feedback tf JOIN inspection_task t ON t.id=tf.task_id WHERE t.enterprise_id=?", enterpriseId);
            int expected = 0;
            for (MockSourceService.PointDef p : MockSourceService.POINTS) {
                if (p.enterpriseId().equals(enterpriseId)) expected += p.pollutants().size() * 24 * 7;
            }
            int actual = n("SELECT COUNT(*) FROM monitoring_record WHERE enterprise_id=? AND monitor_time >= ?",
                    enterpriseId, Timestamp.valueOf(sevenDaysAgo));
            int completeness = expected == 0 ? 100 : Math.min(100, actual * 100 / expected);
            int score = (int) Math.round(high * 16 + medium * 8 + low * 3 + (100 - completeness) * 0.35 + pendingTasks * 4);
            score = Math.max(0, Math.min(100, score));
            String level = score < 25 ? "A" : score < 50 ? "B" : score < 75 ? "C" : "D";
            Map<String, Object> factors = new LinkedHashMap<>();
            factors.put("highEvents", high);
            factors.put("mediumEvents", medium);
            factors.put("lowEvents", low);
            factors.put("dataCompleteness", completeness);
            factors.put("pendingTasks", pendingTasks);
            factors.put("feedbackCount", feedbacks);
            factors.put("weightVersion", "risk-v1");
            try {
                String factorsJson = objectMapper.writeValueAsString(factors);
                jdbc.update("UPDATE enterprise SET risk_score=?, risk_level=? WHERE id=?", score, level, enterpriseId);
                jdbc.update("INSERT INTO risk_snapshot(enterprise_id,score,level,factors_json,weight_version) VALUES (?,?,?,?,?)",
                        enterpriseId, score, level, factorsJson, "risk-v1");
            } catch (Exception ex) {
                throw new IllegalStateException(ex);
            }
        }
    }

    public List<Map<String, Object>> listEvents(String status, String priority, String enterpriseId, String keyword) {
        StringBuilder sql = new StringBuilder("""
                SELECT ev.id, ev.event_no AS eventNo, ev.title, ev.priority, ev.max_severity AS maxSeverity,
                       ev.risk_score AS riskScore, ev.credibility, ev.status, ev.clue_count AS clueCount,
                       FORMATDATETIME(ev.start_time,'yyyy-MM-dd HH:mm') AS startTime,
                       FORMATDATETIME(ev.end_time,'yyyy-MM-dd HH:mm') AS endTime,
                       e.id AS enterpriseId, e.name AS enterpriseName, e.region,
                       p.point_name AS pointName, po.name AS pollutantName, po.code AS pollutantCode
                FROM abnormal_event ev
                JOIN enterprise e ON e.id=ev.enterprise_id
                JOIN monitor_point p ON p.id=ev.point_id
                JOIN pollutant po ON po.code=ev.pollutant_code
                WHERE 1=1
                """);
        List<Object> args = new ArrayList<>();
        if (status != null && !status.isBlank()) {
            sql.append(" AND ev.status=?");
            args.add(status);
        }
        if (priority != null && !priority.isBlank()) {
            sql.append(" AND ev.priority=?");
            args.add(priority);
        }
        if (enterpriseId != null && !enterpriseId.isBlank()) {
            sql.append(" AND ev.enterprise_id=?");
            args.add(enterpriseId);
        }
        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND (ev.title LIKE ? OR e.name LIKE ?)");
            args.add("%" + keyword + "%");
            args.add("%" + keyword + "%");
        }
        sql.append(" ORDER BY CASE ev.priority WHEN 'P1' THEN 1 WHEN 'P2' THEN 2 ELSE 3 END, ev.start_time DESC LIMIT 200");
        return jdbc.queryForList(sql.toString(), args.toArray());
    }

    public Map<String, Object> getEvent(long id) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT ev.id, ev.event_no AS eventNo, ev.title, ev.priority, ev.max_severity AS maxSeverity,
                       ev.risk_score AS riskScore, ev.credibility, ev.status, ev.clue_count AS clueCount,
                       FORMATDATETIME(ev.start_time,'yyyy-MM-dd HH:mm') AS startTime,
                       FORMATDATETIME(ev.end_time,'yyyy-MM-dd HH:mm') AS endTime,
                       e.id AS enterpriseId, e.name AS enterpriseName, e.industry, e.region, e.risk_level AS riskLevel,
                       p.id AS pointId, p.point_name AS pointName, p.outlet_name AS outletName,
                       po.code AS pollutantCode, po.name AS pollutantName, po.unit, po.limit_value AS limitValue,
                       po.standard_ref AS standardRef
                FROM abnormal_event ev
                JOIN enterprise e ON e.id=ev.enterprise_id
                JOIN monitor_point p ON p.id=ev.point_id
                JOIN pollutant po ON po.code=ev.pollutant_code
                WHERE ev.id=?
                """, id);
        if (rows.isEmpty()) return null;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("event", rows.get(0));
        List<Map<String, Object>> clues = jdbc.queryForList("""
                SELECT c.id, c.clue_no AS clueNo, c.rule_code AS ruleCode, c.rule_name AS ruleName, c.severity,
                       c.status, c.trigger_value AS triggerValue, c.threshold_value AS thresholdValue,
                       c.credibility, c.ai_status AS aiStatus,
                       FORMATDATETIME(c.start_time,'yyyy-MM-dd HH:mm') AS startTime,
                       FORMATDATETIME(c.end_time,'yyyy-MM-dd HH:mm') AS endTime, c.evidence_json AS evidenceJson
                FROM clue c WHERE c.event_id=? ORDER BY c.severity, c.start_time
                """, id);
        for (Map<String, Object> clue : clues) {
            try {
                Object raw = clue.remove("evidenceJson");
                clue.put("evidence", raw == null ? Map.of() : objectMapper.readValue(String.valueOf(raw), new TypeReference<>() {}));
            } catch (Exception ignored) {
                clue.put("evidence", Map.of());
            }
            Long clueId = ((Number) clue.get("id")).longValue();
            List<Map<String, Object>> ai = jdbc.queryForList("""
                    SELECT content_json AS contentJson, citations_json AS citationsJson, confidence, refusal,
                           model, prompt_version AS promptVersion,
                           FORMATDATETIME(created_at,'yyyy-MM-dd HH:mm:ss') AS createdAt
                    FROM ai_assessment WHERE clue_id=? ORDER BY id DESC LIMIT 1
                    """, clueId);
            if (!ai.isEmpty()) {
                Map<String, Object> aiMap = ai.get(0);
                try {
                    aiMap.put("content", objectMapper.readValue(String.valueOf(aiMap.get("contentJson")), new TypeReference<>() {}));
                    aiMap.put("citations", objectMapper.readValue(String.valueOf(aiMap.get("citationsJson")), new TypeReference<>() {}));
                } catch (Exception ignored) {
                }
                clue.put("ai", aiMap);
            }
        }
        result.put("clues", clues);
        result.put("tasks", jdbc.queryForList("""
                SELECT t.id, t.task_no AS taskNo, t.clue_id AS clueId, t.status, t.assignee, t.external_no AS externalNo,
                       FORMATDATETIME(t.created_at,'yyyy-MM-dd HH:mm') AS createdAt,
                       FORMATDATETIME(t.due_at,'yyyy-MM-dd HH:mm') AS dueAt,
                       FORMATDATETIME(t.dispatched_at,'yyyy-MM-dd HH:mm') AS dispatchedAt,
                       FORMATDATETIME(t.feedback_at,'yyyy-MM-dd HH:mm') AS feedbackAt
                FROM inspection_task t WHERE t.event_id=? ORDER BY t.id DESC
                """, id));
        return result;
    }

    public Map<String, Object> mergeEvents(List<Long> eventIds, String operator) {
        if (eventIds == null || eventIds.size() < 2) {
            throw new IllegalArgumentException("至少选择两个事件");
        }
        List<Map<String, Object>> events = new ArrayList<>();
        for (Long id : eventIds) {
            List<Map<String, Object>> rows = jdbc.queryForList("""
                    SELECT id, event_no AS eventNo, enterprise_id AS enterpriseId, point_id AS pointId,
                           pollutant_code AS pollutantCode, start_time AS startTime
                    FROM abnormal_event WHERE id=?
                    """, id);
            if (rows.isEmpty()) throw new IllegalArgumentException("事件不存在: " + id);
            events.add(rows.get(0));
        }
        Map<String, Object> first = events.get(0);
        String group = first.get("enterpriseId") + "|" + first.get("pointId") + "|" + first.get("pollutantCode");
        LocalDate date = ((Timestamp) first.get("startTime")).toLocalDateTime().toLocalDate();
        for (Map<String, Object> event : events) {
            String currentGroup = event.get("enterpriseId") + "|" + event.get("pointId") + "|" + event.get("pollutantCode");
            LocalDate currentDate = ((Timestamp) event.get("startTime")).toLocalDateTime().toLocalDate();
            if (!group.equals(currentGroup) || !date.equals(currentDate)) {
                throw new IllegalArgumentException("只能合并同一企业、同一排口、同一污染物、同一天的异常事件");
            }
        }
        long targetId = ((Number) first.get("id")).longValue();
        int moved = 0;
        for (int i = 1; i < events.size(); i++) {
            long sourceId = ((Number) events.get(i).get("id")).longValue();
            moved += jdbc.update("UPDATE clue SET event_id=? WHERE event_id=?", targetId, sourceId);
            jdbc.update("UPDATE inspection_task SET event_id=? WHERE event_id=?", targetId, sourceId);
            jdbc.update("DELETE FROM abnormal_event WHERE id=?", sourceId);
        }
        rebuildForDate(date);
        rebuildRiskForAll();
        jdbc.update("INSERT INTO audit_log(action,target_type,target_id,operator,detail) VALUES (?,?,?,?,?)",
                "MERGE_EVENT", "EVENT", String.valueOf(targetId), operator,
                "合并 " + (events.size() - 1) + " 个事件，迁移线索 " + moved + " 条");
        return getEvent(targetId);
    }

    public Map<String, Object> splitEvent(long eventId, List<Long> clueIds, String operator) {
        if (clueIds == null || clueIds.isEmpty()) {
            throw new IllegalArgumentException("至少选择一条线索拆出");
        }
        List<Map<String, Object>> eventRows = jdbc.queryForList("""
                SELECT id, event_key AS eventKey, enterprise_id AS enterpriseId, point_id AS pointId,
                       pollutant_code AS pollutantCode, start_time AS startTime, title
                FROM abnormal_event WHERE id=?
                """, eventId);
        if (eventRows.isEmpty()) throw new IllegalArgumentException("事件不存在");
        Map<String, Object> source = eventRows.get(0);
        List<Map<String, Object>> owned = jdbc.queryForList("SELECT id FROM clue WHERE event_id=?", eventId);
        if (owned.isEmpty() || clueIds.size() >= owned.size()) {
            throw new IllegalArgumentException("拆分后原事件至少保留一条线索");
        }
        java.util.Set<Long> ownedIds = new java.util.HashSet<>();
        for (Map<String, Object> row : owned) ownedIds.add(((Number) row.get("id")).longValue());
        for (Long clueId : clueIds) {
            if (!ownedIds.contains(clueId)) throw new IllegalArgumentException("选中线索不属于该事件");
        }
        LocalDate date = ((Timestamp) source.get("startTime")).toLocalDateTime().toLocalDate();
        String eventNo = "EV" + date.format(DateTimeFormatter.BASIC_ISO_DATE)
                + "S" + String.format("%03d", ThreadLocalRandom.current().nextInt(1000));
        String eventKey = source.get("eventKey") + "|split-" + System.currentTimeMillis();
        String splitTitle = "拆分事件：" + source.get("title");
        if (splitTitle.length() > 200) splitTitle = splitTitle.substring(0, 200);
        jdbc.update("""
                INSERT INTO abnormal_event(event_no,event_key,enterprise_id,point_id,pollutant_code,start_time,end_time,
                    title,clue_count,max_severity,priority,risk_score,credibility,status)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """, eventNo, eventKey, source.get("enterpriseId"), source.get("pointId"), source.get("pollutantCode"),
                source.get("startTime"), source.get("startTime"), splitTitle, 0, "MEDIUM", "P3", 0, 60, "PENDING");
        Long newId = jdbc.queryForObject("SELECT id FROM abnormal_event WHERE event_key=?", Long.class, eventKey);
        for (Long clueId : clueIds) {
            jdbc.update("UPDATE clue SET event_id=? WHERE id=? AND event_id=?", newId, clueId, eventId);
            jdbc.update("UPDATE inspection_task SET event_id=? WHERE clue_id=? AND event_id=?", newId, clueId, eventId);
        }
        rebuildForDate(date);
        rebuildRiskForAll();
        jdbc.update("INSERT INTO audit_log(action,target_type,target_id,operator,detail) VALUES (?,?,?,?,?)",
                "SPLIT_EVENT", "EVENT", String.valueOf(eventId), operator, "拆出 " + clueIds.size() + " 条线索到事件 " + newId);
        return getEvent(newId);
    }

    public void refreshEventStatus(long eventId) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT start_time AS startTime FROM abnormal_event WHERE id=?", eventId);
        if (!rows.isEmpty()) {
            Timestamp ts = (Timestamp) rows.get(0).get("startTime");
            rebuildForDate(ts.toLocalDateTime().toLocalDate());
            rebuildRiskForAll();
        }
    }

    public List<Map<String, Object>> riskRanking() {
        return jdbc.queryForList("""
                SELECT id, name, industry, region, risk_score AS riskScore, risk_level AS riskLevel,
                       management_level AS managementLevel
                FROM enterprise WHERE status='ACTIVE' OR status IS NULL
                ORDER BY risk_score DESC, name LIMIT 10
                """);
    }

    private int n(String sql, Object... args) {
        Integer value = jdbc.queryForObject(sql, Integer.class, args);
        return value == null ? 0 : value;
    }

    private int severityWeight(String severity) {
        return switch (severity) {
            case "HIGH" -> 3;
            case "MEDIUM" -> 2;
            default -> 1;
        };
    }
}
