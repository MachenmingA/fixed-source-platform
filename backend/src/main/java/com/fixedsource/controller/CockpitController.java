package com.fixedsource.controller;

import com.fixedsource.service.EventRiskService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/cockpit")
public class CockpitController {

    private final JdbcTemplate jdbc;
    private final EventRiskService eventRiskService;

    public CockpitController(JdbcTemplate jdbc, EventRiskService eventRiskService) {
        this.jdbc = jdbc;
        this.eventRiskService = eventRiskService;
    }

    @GetMapping("/overview")
    public Map<String, Object> overview() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("enterpriseCount", n("SELECT COUNT(*) FROM enterprise"));
        result.put("regionCount", n("SELECT COUNT(DISTINCT region) FROM enterprise"));
        result.put("highRiskCount", n("SELECT COUNT(*) FROM enterprise WHERE risk_level IN ('C','D')"));
        result.put("eventPending", n("SELECT COUNT(*) FROM abnormal_event WHERE status NOT IN ('CLOSED')"));
        result.put("taskOpen", n("SELECT COUNT(*) FROM inspection_task WHERE status NOT IN ('CLOSED')"));
        result.put("aiRefusal", n("SELECT COUNT(*) FROM ai_assessment WHERE refusal=TRUE"));
        int totalTasks = n("SELECT COUNT(*) FROM inspection_task");
        int closed = n("SELECT COUNT(*) FROM inspection_task WHERE status='CLOSED'");
        result.put("closureRate", totalTasks == 0 ? 0 : Math.round(closed * 1000.0 / totalTasks) / 10.0);

        List<Map<String, Object>> regions = jdbc.queryForList("""
                SELECT region, COUNT(*) AS enterpriseCount, ROUND(AVG(risk_score),1) AS avgRisk,
                       SUM(CASE WHEN risk_level IN ('C','D') THEN 1 ELSE 0 END) AS highRisk
                FROM enterprise GROUP BY region ORDER BY avgRisk DESC
                """);
        Map<String, Map<String, Object>> byRegion = new LinkedHashMap<>();
        for (Map<String, Object> row : regions) {
            Map<String, Object> item = new LinkedHashMap<>(row);
            item.put("eventCount", 0);
            item.put("closedEvents", 0);
            item.put("taskCount", 0);
            item.put("closedTasks", 0);
            byRegion.put(String.valueOf(row.get("region")), item);
        }
        for (Map<String, Object> row : jdbc.queryForList("""
                SELECT e.region, COUNT(ev.id) AS eventCount, SUM(CASE WHEN ev.status='CLOSED' THEN 1 ELSE 0 END) AS closedEvents
                FROM enterprise e LEFT JOIN abnormal_event ev ON ev.enterprise_id=e.id
                GROUP BY e.region
                """)) {
            Map<String, Object> item = byRegion.get(String.valueOf(row.get("region")));
            if (item != null) {
                item.put("eventCount", row.get("eventCount"));
                item.put("closedEvents", row.get("closedEvents"));
            }
        }
        for (Map<String, Object> row : jdbc.queryForList("""
                SELECT e.region, COUNT(t.id) AS taskCount, SUM(CASE WHEN t.status='CLOSED' THEN 1 ELSE 0 END) AS closedTasks
                FROM enterprise e LEFT JOIN inspection_task t ON t.enterprise_id=e.id
                GROUP BY e.region
                """)) {
            Map<String, Object> item = byRegion.get(String.valueOf(row.get("region")));
            if (item != null) {
                item.put("taskCount", row.get("taskCount"));
                item.put("closedTasks", row.get("closedTasks"));
            }
        }
        result.put("regions", new ArrayList<>(byRegion.values()));
        result.put("industries", jdbc.queryForList("""
                SELECT e.industry, COUNT(DISTINCT e.id) AS enterpriseCount, ROUND(AVG(e.risk_score),1) AS avgRisk,
                       SUM(CASE WHEN e.risk_level IN ('C','D') THEN 1 ELSE 0 END) AS highRisk,
                       (SELECT COUNT(*) FROM abnormal_event ev JOIN enterprise e2 ON e2.id=ev.enterprise_id
                        WHERE e2.industry=e.industry) AS eventCount
                FROM enterprise e GROUP BY e.industry ORDER BY avgRisk DESC
                """));
        result.put("riskMatrix", jdbc.queryForList("""
                SELECT region, risk_level AS riskLevel, COUNT(*) AS count
                FROM enterprise GROUP BY region, risk_level ORDER BY region, risk_level
                """));
        result.put("ruleByRegion", jdbc.queryForList("""
                SELECT e.region, c.rule_name AS ruleName, COUNT(*) AS count
                FROM clue c JOIN enterprise e ON e.id=c.enterprise_id
                GROUP BY e.region, c.rule_name ORDER BY count DESC LIMIT 40
                """));
        result.put("topEvents", eventRiskService.listEvents(null, null, null, null).stream().limit(8).toList());
        return result;
    }

    private int n(String sql) {
        Integer value = jdbc.queryForObject(sql, Integer.class);
        return value == null ? 0 : value;
    }
}
