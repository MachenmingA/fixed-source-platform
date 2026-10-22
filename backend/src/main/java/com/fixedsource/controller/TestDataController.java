package com.fixedsource.controller;

import com.fixedsource.service.BacktestService;
import com.fixedsource.service.LabelService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/test-data")
public class TestDataController {

    private final LabelService labelService;
    private final BacktestService backtestService;
    private final JdbcTemplate jdbc;

    public TestDataController(LabelService labelService, BacktestService backtestService, JdbcTemplate jdbc) {
        this.labelService = labelService;
        this.backtestService = backtestService;
        this.jdbc = jdbc;
    }

    @GetMapping("/labels")
    public List<Map<String, Object>> labels(@RequestParam(required = false) String startDate,
                                            @RequestParam(required = false) String endDate) {
        LocalDate end = endDate == null || endDate.isBlank() ? LocalDate.now() : LocalDate.parse(endDate);
        LocalDate start = startDate == null || startDate.isBlank() ? end.minusDays(7) : LocalDate.parse(startDate);
        return labelService.labels(start, end);
    }

    @GetMapping("/summary")
    public Map<String, Object> summary(@RequestParam(required = false) String startDate,
                                       @RequestParam(required = false) String endDate) {
        LocalDate end = endDate == null || endDate.isBlank() ? LocalDate.now() : LocalDate.parse(endDate);
        LocalDate start = startDate == null || startDate.isBlank() ? end.minusDays(7) : LocalDate.parse(startDate);
        Map<String, Object> result = new LinkedHashMap<>(backtestService.evaluate(start, end));
        result.put("scenarios", jdbc.queryForList("""
                SELECT anomaly_type AS anomalyType, rule_code AS ruleCode, COUNT(*) AS count
                FROM test_anomaly_label WHERE biz_date >= ? AND biz_date <= ?
                GROUP BY anomaly_type, rule_code ORDER BY count DESC
                """, java.sql.Date.valueOf(start), java.sql.Date.valueOf(end)));
        result.put("labelCount", jdbc.queryForObject("""
                SELECT COUNT(*) FROM test_anomaly_label WHERE biz_date >= ? AND biz_date <= ?
                """, Integer.class, java.sql.Date.valueOf(start), java.sql.Date.valueOf(end)));
        return result;
    }
}
