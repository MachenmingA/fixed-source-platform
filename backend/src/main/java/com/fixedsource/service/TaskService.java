package com.fixedsource.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class TaskService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final EventRiskService eventRiskService;

    public TaskService(JdbcTemplate jdbc, ObjectMapper objectMapper, EventRiskService eventRiskService) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.eventRiskService = eventRiskService;
    }

    public Map<String, Object> createFromClue(long clueId, String operator) {
        List<Map<String, Object>> existing = jdbc.queryForList("SELECT id FROM inspection_task WHERE clue_id=?", clueId);
        if (!existing.isEmpty()) {
            return getTask(((Number) existing.get(0).get("id")).longValue());
        }
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT c.id, c.enterprise_id AS enterpriseId, c.event_id AS eventId, c.status,
                       c.rule_name AS ruleName, c.pollutant_code AS pollutantCode, po.name AS pollutantName,
                       p.point_name AS pointName, e.name AS enterpriseName
                FROM clue c JOIN pollutant po ON po.code=c.pollutant_code
                JOIN monitor_point p ON p.id=c.point_id JOIN enterprise e ON e.id=c.enterprise_id
                WHERE c.id=?
                """, clueId);
        if (rows.isEmpty()) throw new IllegalArgumentException("线索不存在");
        Map<String, Object> c = rows.get(0);
        if (!"CONFIRMED".equals(String.valueOf(c.get("status")))) {
            throw new IllegalStateException("请先确认线索，再创建核查任务");
        }
        String taskNo = "TSK-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))
                + "-" + String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
        String title = c.get("pollutantName") + " · " + c.get("ruleName") + " · " + c.get("pointName");
        jdbc.update("""
                INSERT INTO inspection_task(task_no,clue_id,event_id,enterprise_id,title,status,assignee,sla_hours,due_at)
                VALUES (?,?,?,?,?,?,?,?,?)
                """, taskNo, clueId, c.get("eventId"), c.get("enterpriseId"), title, "PENDING_DISPATCH",
                "现场核查一组", 24, Timestamp.valueOf(LocalDateTime.now().plusHours(24)));
        Long id = jdbc.queryForObject("SELECT id FROM inspection_task WHERE task_no=?", Long.class, taskNo);
        audit("CREATE_TASK", "TASK", taskNo, operator, "由线索 " + clueId + " 创建核查任务");
        return getTask(id);
    }

    public Map<String, Object> dispatch(long taskId, String operator) {
        Map<String, Object> task = rawTask(taskId);
        if (task == null) throw new IllegalArgumentException("任务不存在");
        if (!List.of("PENDING_DISPATCH", "DISPATCH_FAILED").contains(String.valueOf(task.get("status")))) {
            throw new IllegalStateException("当前任务状态不允许下发");
        }
        String taskNo = String.valueOf(task.get("taskNo"));
        String externalNo = "EXT-" + taskNo;
        String messageNo = "MSG-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("taskNo", taskNo);
        request.put("enterpriseId", task.get("enterpriseId"));
        request.put("title", task.get("title"));
        request.put("target", "mobile-inspection");
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("accepted", true);
        response.put("externalNo", externalNo);
        try {
            jdbc.update("""
                    INSERT INTO dispatch_outbox(message_no,task_id,task_no,target,status,request_json,response_json)
                    VALUES (?,?,?,?,?,?,?)
                    """, messageNo, taskId, taskNo, "MOBILE", "SENT",
                    objectMapper.writeValueAsString(request), objectMapper.writeValueAsString(response));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        jdbc.update("""
                UPDATE inspection_task SET status='DISPATCHED', external_no=?, dispatched_at=? WHERE id=?
                """, externalNo, Timestamp.valueOf(LocalDateTime.now()), taskId);
        jdbc.update("UPDATE clue SET status='DISPATCHED' WHERE id=?", task.get("clueId"));
        Long eventId = task.get("eventId") == null ? null : ((Number) task.get("eventId")).longValue();
        if (eventId != null) eventRiskService.refreshEventStatus(eventId);
        audit("DISPATCH_TASK", "TASK", taskNo, operator, "下发到模拟移动端，外部单号 " + externalNo);
        return getTask(taskId);
    }

    public Map<String, Object> accept(String taskNo, String operator) {
        Map<String, Object> task = rawTaskByNo(taskNo);
        if (task == null) throw new IllegalArgumentException("任务不存在");
        jdbc.update("UPDATE inspection_task SET status='IN_PROGRESS' WHERE task_no=? AND status='DISPATCHED'", taskNo);
        jdbc.update("UPDATE clue SET status='IN_PROGRESS' WHERE id=?", task.get("clueId"));
        audit("ACCEPT_TASK", "TASK", taskNo, operator, "现场人员接收任务");
        return getTaskByNo(taskNo);
    }

    public Map<String, Object> feedback(String taskNo, Map<String, Object> body, String operator) {
        Map<String, Object> task = rawTaskByNo(taskNo);
        if (task == null) throw new IllegalArgumentException("任务不存在");
        if ("CLOSED".equals(String.valueOf(task.get("status")))) {
            throw new IllegalStateException("已完成任务不能重复提交反馈");
        }
        String feedbackNo = str(body, "feedbackNo");
        if (feedbackNo == null || feedbackNo.isBlank()) {
            feedbackNo = "FB-" + taskNo;
        }
        String inspector = str(body, "inspector");
        if (inspector == null || inspector.isBlank()) inspector = operator;
        String result = str(body, "result");
        if (result == null) result = "NORMAL";
        List<Map<String, Object>> existing = jdbc.queryForList("SELECT id FROM task_feedback WHERE feedback_no=?", feedbackNo);
        if (existing.isEmpty()) {
            jdbc.update("""
                    INSERT INTO task_feedback(feedback_no,task_id,task_no,inspector,result,problem,disposal,attachment_url,watermark,latitude,longitude)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?)
                    """, feedbackNo, task.get("id"), taskNo, inspector, result, str(body, "problem"),
                    str(body, "disposal"), str(body, "attachmentUrl"), str(body, "watermark"),
                    num(body, "latitude"), num(body, "longitude"));
        } else {
            jdbc.update("""
                    UPDATE task_feedback SET inspector=?, result=?, problem=?, disposal=?, attachment_url=?, watermark=?,
                        latitude=?, longitude=?, submitted_at=CURRENT_TIMESTAMP WHERE feedback_no=?
                    """, inspector, result, str(body, "problem"), str(body, "disposal"), str(body, "attachmentUrl"),
                    str(body, "watermark"), num(body, "latitude"), num(body, "longitude"), feedbackNo);
        }
        jdbc.update("UPDATE inspection_task SET status='FEEDBACK', feedback_at=? WHERE task_no=?",
                Timestamp.valueOf(LocalDateTime.now()), taskNo);
        jdbc.update("UPDATE clue SET status='FEEDBACK' WHERE id=?", task.get("clueId"));
        Long eventId = task.get("eventId") == null ? null : ((Number) task.get("eventId")).longValue();
        if (eventId != null) eventRiskService.refreshEventStatus(eventId);
        audit("SUBMIT_FEEDBACK", "TASK", taskNo, operator, "反馈编号 " + feedbackNo + "，结果 " + result);
        return getTaskByNo(taskNo);
    }

    public Map<String, Object> close(long taskId, String operator) {
        Map<String, Object> task = rawTask(taskId);
        if (task == null) throw new IllegalArgumentException("任务不存在");
        jdbc.update("UPDATE inspection_task SET status='CLOSED', closed_at=? WHERE id=?",
                Timestamp.valueOf(LocalDateTime.now()), taskId);
        jdbc.update("UPDATE clue SET status='CLOSED' WHERE id=?", task.get("clueId"));
        Long eventId = task.get("eventId") == null ? null : ((Number) task.get("eventId")).longValue();
        if (eventId != null) eventRiskService.refreshEventStatus(eventId);
        audit("CLOSE_TASK", "TASK", String.valueOf(task.get("taskNo")), operator, "关闭核查任务");
        return getTask(taskId);
    }

    public Map<String, Object> urge(long taskId, String operator) {
        Map<String, Object> task = rawTask(taskId);
        if (task == null) throw new IllegalArgumentException("任务不存在");
        jdbc.update("UPDATE inspection_task SET urge_count=urge_count+1 WHERE id=?", taskId);
        try {
            jdbc.update("""
                    INSERT INTO dispatch_outbox(message_no,task_id,task_no,target,status,request_json,response_json)
                    VALUES (?,?,?,?,?,?,?)
                    """, "URGE-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS")),
                    taskId, task.get("taskNo"), "MOBILE", "SENT",
                    objectMapper.writeValueAsString(Map.of("type", "URGE", "taskNo", task.get("taskNo"))),
                    objectMapper.writeValueAsString(Map.of("accepted", true)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        audit("URGE_TASK", "TASK", String.valueOf(task.get("taskNo")), operator, "督办任务");
        return getTask(taskId);
    }

    public List<Map<String, Object>> listTasks(String status, String keyword) {
        StringBuilder sql = new StringBuilder("""
                SELECT t.id, t.task_no AS taskNo, t.title, t.status, t.assignee, t.external_no AS externalNo,
                       t.clue_id AS clueId, t.event_id AS eventId, t.urge_count AS urgeCount,
                       FORMATDATETIME(t.due_at,'yyyy-MM-dd HH:mm') AS dueAt,
                       FORMATDATETIME(t.dispatched_at,'yyyy-MM-dd HH:mm') AS dispatchedAt,
                       FORMATDATETIME(t.feedback_at,'yyyy-MM-dd HH:mm') AS feedbackAt,
                       FORMATDATETIME(t.closed_at,'yyyy-MM-dd HH:mm') AS closedAt,
                       CASE WHEN t.status NOT IN ('CLOSED') AND t.due_at < CURRENT_TIMESTAMP THEN 'OVERDUE' ELSE 'NORMAL' END AS slaStatus,
                       e.name AS enterpriseName, e.region, p.point_name AS pointName,
                       po.name AS pollutantName, po.code AS pollutantCode, c.rule_name AS ruleName,
                       c.severity, c.credibility
                FROM inspection_task t
                JOIN enterprise e ON e.id=t.enterprise_id
                LEFT JOIN clue c ON c.id=t.clue_id
                LEFT JOIN monitor_point p ON p.id=c.point_id
                LEFT JOIN pollutant po ON po.code=c.pollutant_code
                WHERE 1=1
                """);
        List<Object> args = new ArrayList<>();
        if (status != null && !status.isBlank()) {
            sql.append(" AND t.status=?");
            args.add(status);
        }
        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND (t.title LIKE ? OR e.name LIKE ? OR t.task_no LIKE ?)");
            args.add("%" + keyword + "%");
            args.add("%" + keyword + "%");
            args.add("%" + keyword + "%");
        }
        sql.append(" ORDER BY CASE t.status WHEN 'FEEDBACK' THEN 1 WHEN 'DISPATCHED' THEN 2 WHEN 'IN_PROGRESS' THEN 3 ELSE 4 END, t.id DESC LIMIT 200");
        return jdbc.queryForList(sql.toString(), args.toArray());
    }

    public List<Map<String, Object>> mobileTasks() {
        return jdbc.queryForList("""
                SELECT t.id, t.task_no AS taskNo, t.title, t.status, t.assignee,
                       FORMATDATETIME(t.due_at,'yyyy-MM-dd HH:mm') AS dueAt,
                       t.urge_count AS urgeCount,
                       CASE WHEN t.status NOT IN ('CLOSED') AND t.due_at < CURRENT_TIMESTAMP THEN 'OVERDUE' ELSE 'NORMAL' END AS slaStatus,
                       e.name AS enterpriseName, e.region, p.point_name AS pointName,
                       po.name AS pollutantName, c.rule_name AS ruleName, c.severity, c.credibility
                FROM inspection_task t
                JOIN enterprise e ON e.id=t.enterprise_id
                LEFT JOIN clue c ON c.id=t.clue_id
                LEFT JOIN monitor_point p ON p.id=c.point_id
                LEFT JOIN pollutant po ON po.code=c.pollutant_code
                WHERE t.status IN ('DISPATCHED','IN_PROGRESS','FEEDBACK')
                ORDER BY CASE t.status WHEN 'DISPATCHED' THEN 1 WHEN 'IN_PROGRESS' THEN 2 ELSE 3 END, t.due_at
                """);
    }

    public Map<String, Object> getTask(long id) {
        return buildTaskDetail(rawTask(id));
    }

    public Map<String, Object> getTaskByNo(String taskNo) {
        return buildTaskDetail(rawTaskByNo(taskNo));
    }

    private Map<String, Object> buildTaskDetail(Map<String, Object> task) {
        if (task == null) return null;
        Map<String, Object> result = new LinkedHashMap<>(task);
        List<Map<String, Object>> feedback = jdbc.queryForList("""
                SELECT id, feedback_no AS feedbackNo, inspector, result, problem, disposal,
                       attachment_url AS attachmentUrl, watermark, latitude, longitude,
                       FORMATDATETIME(submitted_at,'yyyy-MM-dd HH:mm:ss') AS submittedAt
                FROM task_feedback WHERE task_no=? ORDER BY id DESC LIMIT 1
                """, task.get("taskNo"));
        result.put("feedback", feedback.isEmpty() ? null : feedback.get(0));
        List<Map<String, Object>> clue = jdbc.queryForList("""
                SELECT c.id, c.clue_no AS clueNo, c.rule_code AS ruleCode, c.rule_name AS ruleName, c.severity,
                       c.trigger_value AS triggerValue, c.threshold_value AS thresholdValue,
                       c.evidence_json AS evidenceJson, c.ai_status AS aiStatus, c.credibility,
                       FORMATDATETIME(c.start_time,'yyyy-MM-dd HH:mm') AS startTime,
                       FORMATDATETIME(c.end_time,'yyyy-MM-dd HH:mm') AS endTime,
                       p.point_name AS pointName, po.name AS pollutantName
                FROM clue c
                LEFT JOIN monitor_point p ON p.id=c.point_id
                LEFT JOIN pollutant po ON po.code=c.pollutant_code
                WHERE c.id=?
                """, task.get("clueId"));
        if (!clue.isEmpty()) {
            Map<String, Object> c = clue.get(0);
            try {
                Object raw = c.remove("evidenceJson");
                c.put("evidence", raw == null ? Map.of() : objectMapper.readValue(String.valueOf(raw), new TypeReference<>() {}));
            } catch (Exception ignored) {
                c.put("evidence", Map.of());
            }
            List<Map<String, Object>> ai = jdbc.queryForList("""
                    SELECT content_json AS contentJson, citations_json AS citationsJson, confidence, refusal,
                           model, prompt_version AS promptVersion
                    FROM ai_assessment WHERE clue_id=? ORDER BY id DESC LIMIT 1
                    """, c.get("id"));
            if (!ai.isEmpty()) {
                Map<String, Object> row = ai.get(0);
                try {
                    c.put("aiContent", objectMapper.readValue(String.valueOf(row.get("contentJson")), new TypeReference<>() {}));
                    c.put("aiCitations", objectMapper.readValue(String.valueOf(row.get("citationsJson")), new TypeReference<>() {}));
                } catch (Exception ignored) {
                }
            }
            result.put("clue", c);
        }
        return result;
    }

    private Map<String, Object> rawTask(long id) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT t.id, t.task_no AS taskNo, t.title, t.status, t.assignee, t.external_no AS externalNo,
                       t.clue_id AS clueId, t.event_id AS eventId, t.enterprise_id AS enterpriseId,
                       t.sla_hours AS slaHours, t.urge_count AS urgeCount,
                       FORMATDATETIME(t.due_at,'yyyy-MM-dd HH:mm') AS dueAt,
                       FORMATDATETIME(t.dispatched_at,'yyyy-MM-dd HH:mm') AS dispatchedAt,
                       FORMATDATETIME(t.feedback_at,'yyyy-MM-dd HH:mm') AS feedbackAt,
                       e.name AS enterpriseName
                FROM inspection_task t JOIN enterprise e ON e.id=t.enterprise_id WHERE t.id=?
                """, id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private Map<String, Object> rawTaskByNo(String taskNo) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT t.id, t.task_no AS taskNo, t.title, t.status, t.assignee, t.external_no AS externalNo,
                       t.clue_id AS clueId, t.event_id AS eventId, t.enterprise_id AS enterpriseId,
                       t.sla_hours AS slaHours, t.urge_count AS urgeCount,
                       FORMATDATETIME(t.due_at,'yyyy-MM-dd HH:mm') AS dueAt,
                       FORMATDATETIME(t.dispatched_at,'yyyy-MM-dd HH:mm') AS dispatchedAt,
                       FORMATDATETIME(t.feedback_at,'yyyy-MM-dd HH:mm') AS feedbackAt,
                       e.name AS enterpriseName
                FROM inspection_task t JOIN enterprise e ON e.id=t.enterprise_id WHERE t.task_no=?
                """, taskNo);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private String str(Map<String, Object> body, String key) {
        Object value = body == null ? null : body.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private Double num(Map<String, Object> body, String key) {
        Object value = body == null ? null : body.get(key);
        if (value == null) return null;
        try { return Double.valueOf(String.valueOf(value)); } catch (Exception e) { return null; }
    }

    private void audit(String action, String type, String id, String operator, String detail) {
        jdbc.update("INSERT INTO audit_log(action,target_type,target_id,operator,detail) VALUES (?,?,?,?,?)",
                action, type, id, operator, detail);
    }
}
