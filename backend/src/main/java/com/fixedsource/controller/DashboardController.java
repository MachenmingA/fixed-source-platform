package com.fixedsource.controller;

import com.fixedsource.service.EventRiskService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final JdbcTemplate jdbc;
    private final EventRiskService eventRiskService;

    public DashboardController(JdbcTemplate jdbc, EventRiskService eventRiskService) {
        this.jdbc = jdbc;
        this.eventRiskService = eventRiskService;
    }

    @GetMapping("/overview")
    public Map<String, Object> overview() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("enterpriseCount", n("SELECT COUNT(*) FROM enterprise"));
        result.put("activeEnterpriseCount", n("SELECT COUNT(*) FROM enterprise WHERE status='ACTIVE' OR status IS NULL"));
        result.put("inactiveEnterpriseCount", n("SELECT COUNT(*) FROM enterprise WHERE status='INACTIVE'"));
        result.put("highRiskCount", n("SELECT COUNT(*) FROM enterprise WHERE (status='ACTIVE' OR status IS NULL) AND risk_level IN ('C','D')"));
        result.put("pointCount", n("SELECT COUNT(*) FROM monitor_point"));
        result.put("eventPending", n("SELECT COUNT(*) FROM abnormal_event WHERE status NOT IN ('CLOSED')"));
        result.put("cluePending", n("SELECT COUNT(*) FROM clue WHERE status='PENDING'"));
        result.put("taskOpen", n("SELECT COUNT(*) FROM inspection_task WHERE status NOT IN ('CLOSED')"));
        result.put("taskClosed", n("SELECT COUNT(*) FROM inspection_task WHERE status='CLOSED'"));
        result.put("slaOverdue", n("SELECT COUNT(*) FROM inspection_task WHERE status NOT IN ('CLOSED') AND due_at < CURRENT_TIMESTAMP"));
        result.put("totalClues", n("SELECT COUNT(*) FROM clue"));
        result.put("aiDone", n("SELECT COUNT(*) FROM clue WHERE ai_status='DONE'"));
        result.put("aiRefusal", n("SELECT COUNT(*) FROM ai_assessment WHERE refusal=TRUE"));
        result.put("openIssues", n("SELECT COUNT(*) FROM data_quality_issue WHERE status='OPEN'"));
        result.put("qualityIssues7d", n("SELECT COUNT(*) FROM data_quality_issue WHERE monitor_date >= ?",
                java.sql.Date.valueOf(LocalDate.now().minusDays(7))));
        result.put("monitorRowsToday", n("SELECT COUNT(*) FROM monitoring_record WHERE monitor_time >= ? AND monitor_time < ?",
                Timestamp.valueOf(LocalDate.now().atStartOfDay()), Timestamp.valueOf(LocalDate.now().plusDays(1).atStartOfDay())));
        int totalTasks = n("SELECT COUNT(*) FROM inspection_task");
        int closed = n("SELECT COUNT(*) FROM inspection_task WHERE status='CLOSED'");
        result.put("closureRate", totalTasks == 0 ? 0 : Math.round(closed * 1000.0 / totalTasks) / 10.0);
        result.put("riskDistribution", jdbc.queryForList("""
                SELECT risk_level AS level, COUNT(*) AS count FROM enterprise
                WHERE status='ACTIVE' OR status IS NULL GROUP BY risk_level ORDER BY risk_level
                """));
        result.put("eventTrend", jdbc.queryForList("""
                SELECT FORMATDATETIME(start_time,'yyyy-MM-dd') AS bizDay, COUNT(*) AS total,
                       SUM(CASE WHEN max_severity='HIGH' THEN 1 ELSE 0 END) AS highCount
                FROM abnormal_event WHERE start_time >= ?
                GROUP BY FORMATDATETIME(start_time,'yyyy-MM-dd') ORDER BY bizDay
                """, Timestamp.valueOf(LocalDateTime.now().minusDays(7))));
        result.put("regionStats", jdbc.queryForList("""
                SELECT e.region, COUNT(*) AS count, ROUND(AVG(e.risk_score),1) AS avgRisk,
                       SUM(CASE WHEN e.risk_level IN ('C','D') THEN 1 ELSE 0 END) AS highRisk,
                       SUM(CASE WHEN e.status='ACTIVE' OR e.status IS NULL THEN 1 ELSE 0 END) AS activeCount,
                       (SELECT COUNT(*) FROM abnormal_event ev JOIN enterprise x ON x.id=ev.enterprise_id
                        WHERE x.region=e.region AND ev.status <> 'CLOSED') AS eventCount
                FROM enterprise e GROUP BY e.region ORDER BY count DESC, avgRisk DESC
                """));
        result.put("industryStats", jdbc.queryForList("""
                SELECT industry, COUNT(*) AS count, ROUND(AVG(risk_score),1) AS avgRisk,
                       SUM(CASE WHEN risk_level IN ('C','D') THEN 1 ELSE 0 END) AS highRisk
                FROM enterprise WHERE status='ACTIVE' OR status IS NULL
                GROUP BY industry ORDER BY avgRisk DESC, count DESC
                """));
        result.put("ruleStats", jdbc.queryForList("""
                SELECT rule_code AS ruleCode, rule_name AS ruleName, COUNT(*) AS count
                FROM clue GROUP BY rule_code, rule_name ORDER BY count DESC
                """));
        result.put("taskStatus", jdbc.queryForList("""
                SELECT status, COUNT(*) AS count FROM inspection_task GROUP BY status ORDER BY count DESC
                """));
        result.put("eventPriorityStats", jdbc.queryForList("""
                SELECT priority, COUNT(*) AS count FROM abnormal_event
                WHERE status NOT IN ('CLOSED') GROUP BY priority ORDER BY priority
                """));
        result.put("eventStatusStats", jdbc.queryForList("""
                SELECT status, COUNT(*) AS count FROM abnormal_event GROUP BY status ORDER BY count DESC
                """));
        result.put("mapEnterprises", jdbc.queryForList("""
                SELECT e.id, e.name, e.industry, e.region, e.risk_score AS riskScore, e.risk_level AS riskLevel,
                       e.longitude, e.latitude, e.production_status AS productionStatus,
                       (SELECT COUNT(*) FROM monitor_point p WHERE p.enterprise_id=e.id) AS pointCount,
                       (SELECT COUNT(*) FROM abnormal_event ev WHERE ev.enterprise_id=e.id AND ev.status <> 'CLOSED') AS pendingEvents
                FROM enterprise e
                WHERE (e.status='ACTIVE' OR e.status IS NULL) AND e.longitude IS NOT NULL AND e.latitude IS NOT NULL
                ORDER BY e.risk_score DESC, e.name
                """));
        result.put("recentEvents", eventRiskService.listEvents(null, null, null, null).stream().limit(8).toList());
        result.put("riskRanking", eventRiskService.riskRanking());
        result.put("openTasks", jdbc.queryForList("""
                SELECT t.id, t.task_no AS taskNo, t.title, t.status, t.assignee,
                       FORMATDATETIME(t.due_at,'yyyy-MM-dd HH:mm') AS dueAt,
                       t.urge_count AS urgeCount,
                       CASE WHEN t.status NOT IN ('CLOSED') AND t.due_at < CURRENT_TIMESTAMP THEN 'OVERDUE' ELSE 'NORMAL' END AS slaStatus,
                       e.name AS enterpriseName
                FROM inspection_task t JOIN enterprise e ON e.id=t.enterprise_id
                WHERE t.status NOT IN ('CLOSED')
                ORDER BY CASE t.status WHEN 'DISPATCHED' THEN 1 WHEN 'IN_PROGRESS' THEN 2 ELSE 3 END, t.due_at
                LIMIT 5
                """));
        List<Map<String, Object>> latestBatch = jdbc.queryForList("""
                SELECT batch_no AS batchNo, FORMATDATETIME(biz_date,'yyyy-MM-dd') AS bizDate, status,
                       expected_count AS expectedCount, received_count AS receivedCount,
                       success_count AS successCount, failed_count AS failedCount,
                       FORMATDATETIME(started_at,'yyyy-MM-dd HH:mm') AS startedAt,
                       FORMATDATETIME(finished_at,'yyyy-MM-dd HH:mm') AS finishedAt, message
                FROM collect_batch ORDER BY id DESC LIMIT 1
                """);
        result.put("latestBatch", latestBatch.isEmpty() ? null : latestBatch.get(0));
        return result;
    }

    private int n(String sql) {
        Integer value = jdbc.queryForObject(sql, Integer.class);
        return value == null ? 0 : value;
    }

    private int n(String sql, Object... args) {
        Integer value = jdbc.queryForObject(sql, Integer.class, args);
        return value == null ? 0 : value;
    }
}
