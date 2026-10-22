package com.fixedsource.controller;

import com.fixedsource.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/integration")
public class IntegrationController {

    private final JdbcTemplate jdbc;

    public IntegrationController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/outbox")
    public List<Map<String, Object>> outbox(@RequestParam(defaultValue = "50") int limit) {
        return jdbc.queryForList("""
                SELECT o.id, o.message_no AS messageNo, o.task_id AS taskId, o.task_no AS taskNo, o.target,
                       o.status, o.retry_count AS retryCount, o.request_json AS requestJson,
                       o.response_json AS responseJson,
                       FORMATDATETIME(o.created_at,'yyyy-MM-dd HH:mm:ss') AS createdAt,
                       FORMATDATETIME(o.updated_at,'yyyy-MM-dd HH:mm:ss') AS updatedAt,
                       t.status AS taskStatus, t.title, e.name AS enterpriseName
                FROM dispatch_outbox o
                LEFT JOIN inspection_task t ON t.id=o.task_id
                LEFT JOIN enterprise e ON e.id=t.enterprise_id
                ORDER BY o.id DESC LIMIT ?
                """, Math.min(200, Math.max(1, limit)));
    }

    @PostMapping("/outbox/{id}/retry")
    public Map<String, Object> retry(@PathVariable long id, HttpServletRequest request) {
        AuthService.User user = (AuthService.User) request.getAttribute("user");
        jdbc.update("""
                UPDATE dispatch_outbox SET status='SENT', retry_count=retry_count+1, updated_at=CURRENT_TIMESTAMP
                WHERE id=?
                """, id);
        jdbc.update("""
                UPDATE inspection_task SET status='DISPATCHED'
                WHERE id=(SELECT task_id FROM dispatch_outbox WHERE id=?) AND status='DISPATCH_FAILED'
                """, id);
        jdbc.update("INSERT INTO audit_log(action,target_type,target_id,operator,detail) VALUES (?,?,?,?,?)",
                "RETRY_OUTBOX", "OUTBOX", String.valueOf(id), user.name(), "人工重发下发消息");
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM dispatch_outbox WHERE id=?", id);
        return rows.isEmpty() ? Map.of() : rows.get(0);
    }

    @PostMapping("/reconcile")
    public Map<String, Object> reconcile() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("openTasks", n("SELECT COUNT(*) FROM inspection_task WHERE status NOT IN ('CLOSED')"));
        result.put("overdueTasks", n("SELECT COUNT(*) FROM inspection_task WHERE status NOT IN ('CLOSED') AND due_at < CURRENT_TIMESTAMP"));
        result.put("waitingReceipt", n("SELECT COUNT(*) FROM inspection_task WHERE status='DISPATCHED' AND dispatched_at < DATEADD('HOUR', -1, CURRENT_TIMESTAMP)"));
        result.put("failedOutbox", n("SELECT COUNT(*) FROM dispatch_outbox WHERE status='FAILED'"));
        result.put("feedbackTasks", n("SELECT COUNT(*) FROM inspection_task WHERE status='FEEDBACK'"));
        result.put("checkedAt", LocalDateTime.now().toString());
        result.put("diff", n("""
                SELECT COUNT(*) FROM inspection_task t
                WHERE t.status='DISPATCHED' AND NOT EXISTS (
                    SELECT 1 FROM dispatch_outbox o WHERE o.task_id=t.id AND o.status='SENT'
                )
                """));
        jdbc.update("INSERT INTO audit_log(action,target_type,target_id,operator,detail) VALUES (?,?,?,?,?)",
                "RECONCILE", "INTEGRATION", "daily", "system", "执行下发/回执对账");
        return result;
    }

    @GetMapping("/summary")
    public Map<String, Object> summary() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("messages", n("SELECT COUNT(*) FROM dispatch_outbox"));
        result.put("sent", n("SELECT COUNT(*) FROM dispatch_outbox WHERE status='SENT'"));
        result.put("failed", n("SELECT COUNT(*) FROM dispatch_outbox WHERE status='FAILED'"));
        result.put("retried", n("SELECT COUNT(*) FROM dispatch_outbox WHERE retry_count > 0"));
        result.put("openTasks", n("SELECT COUNT(*) FROM inspection_task WHERE status NOT IN ('CLOSED')"));
        result.put("overdueTasks", n("SELECT COUNT(*) FROM inspection_task WHERE status NOT IN ('CLOSED') AND due_at < CURRENT_TIMESTAMP"));
        return result;
    }

    private int n(String sql) {
        Integer value = jdbc.queryForObject(sql, Integer.class);
        return value == null ? 0 : value;
    }
}
