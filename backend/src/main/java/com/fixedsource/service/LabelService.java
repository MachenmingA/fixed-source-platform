package com.fixedsource.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Service
public class LabelService {

    private final JdbcTemplate jdbc;
    private final MockSourceService mockSource;

    public LabelService(JdbcTemplate jdbc, MockSourceService mockSource) {
        this.jdbc = jdbc;
        this.mockSource = mockSource;
    }

    public int syncDate(LocalDate date) {
        int count = 0;
        // 同一业务日期只保留当前稳定剧本生成的标签，重跑采集不会叠加旧标签。
        jdbc.update("DELETE FROM test_anomaly_label WHERE biz_date=?", Date.valueOf(date));
        for (Map<String, Object> label : mockSource.labelsForDate(date)) {
            jdbc.update("""
                    MERGE INTO test_anomaly_label(biz_date,enterprise_id,point_id,pollutant_code,rule_code,
                        anomaly_type,start_hour,end_hour,expected_severity,scenario,description)
                    KEY(biz_date,point_id,pollutant_code,rule_code,start_hour)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?)
                    """, Date.valueOf(date), label.get("enterpriseId"), label.get("pointId"), label.get("pollutantCode"),
                    label.get("ruleCode"), label.get("anomalyType"), label.get("startHour"), label.get("endHour"),
                    label.get("expectedSeverity"), label.get("scenario"), label.get("description"));
            count++;
        }
        return count;
    }

    public int syncRange(LocalDate start, LocalDate end) {
        int count = 0;
        LocalDate date = start;
        while (!date.isAfter(end)) {
            count += syncDate(date);
            date = date.plusDays(1);
        }
        return count;
    }

    public List<Map<String, Object>> labels(LocalDate start, LocalDate end) {
        return jdbc.queryForList("""
                SELECT l.id, FORMATDATETIME(l.biz_date,'yyyy-MM-dd') AS bizDate,
                       l.enterprise_id AS enterpriseId, e.name AS enterpriseName,
                       l.point_id AS pointId, p.point_name AS pointName,
                       l.pollutant_code AS pollutantCode, po.name AS pollutantName,
                       l.rule_code AS ruleCode, l.anomaly_type AS anomalyType,
                       l.start_hour AS startHour, l.end_hour AS endHour,
                       l.expected_severity AS expectedSeverity, l.scenario, l.description,
                       (SELECT COUNT(*) FROM clue c WHERE c.point_id=l.point_id
                          AND c.pollutant_code=l.pollutant_code AND c.rule_code=l.rule_code
                          AND CAST(c.start_time AS DATE)=l.biz_date) AS detectedCount
                FROM test_anomaly_label l
                LEFT JOIN enterprise e ON e.id=l.enterprise_id
                LEFT JOIN monitor_point p ON p.id=l.point_id
                LEFT JOIN pollutant po ON po.code=l.pollutant_code
                WHERE l.biz_date >= ? AND l.biz_date <= ?
                ORDER BY l.biz_date DESC, l.point_id, l.pollutant_code, l.rule_code
                """, Date.valueOf(start), Date.valueOf(end));
    }
}
