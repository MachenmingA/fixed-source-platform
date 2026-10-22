package com.fixedsource.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DataQualityService {

    private final JdbcTemplate jdbc;
    private final MockSourceService mockSource;

    public DataQualityService(JdbcTemplate jdbc, MockSourceService mockSource) {
        this.jdbc = jdbc;
        this.mockSource = mockSource;
    }

    public int recordForDate(String batchNo, LocalDate date) {
        int issues = 0;
        for (MockSourceService.PointDef point : MockSourceService.POINTS) {
            for (String pollutantCode : point.pollutants()) {
                Integer actual = jdbc.queryForObject("""
                        SELECT COUNT(*) FROM monitoring_record
                        WHERE point_id=? AND pollutant_code=? AND monitor_time >= ? AND monitor_time < ?
                        """, Integer.class, point.id(), pollutantCode, Timestamp.valueOf(date.atStartOfDay()),
                        Timestamp.valueOf(date.plusDays(1).atStartOfDay()));
                int missing = 24 - (actual == null ? 0 : actual);
                if (missing > 0) {
                    String severity = missing >= 6 ? "ERROR" : "WARN";
                    String detail = point.pointName() + " " + pollutantCode + " 应收 24 小时，实收 "
                            + (actual == null ? 0 : actual) + " 小时，缺失 " + missing + " 小时";
                    jdbc.update("""
                            MERGE INTO data_quality_issue(batch_no,monitor_date,enterprise_id,point_id,pollutant_code,
                                issue_type,severity,detail,status)
                            KEY(batch_no,monitor_date,point_id,pollutant_code,issue_type)
                            VALUES (?,?,?,?,?,?,?,?,?)
                            """, batchNo, Date.valueOf(date), point.enterpriseId(), point.id(), pollutantCode,
                            "MISSING", severity, detail, "OPEN");
                    issues++;
                }
            }
        }
        return issues;
    }

    public int backfill(LocalDate start, LocalDate end) {
        int count = 0;
        LocalDate date = start;
        while (!date.isAfter(end)) {
            count += recordForDate("SEED-DQ", date);
            date = date.plusDays(1);
        }
        return count;
    }

    public List<Map<String, Object>> list(LocalDate start, LocalDate end, String issueType, String severity) {
        StringBuilder sql = new StringBuilder("""
                SELECT q.id, q.batch_no AS batchNo, FORMATDATETIME(q.monitor_date,'yyyy-MM-dd') AS monitorDate,
                       q.enterprise_id AS enterpriseId, e.name AS enterpriseName,
                       q.point_id AS pointId, p.point_name AS pointName,
                       q.pollutant_code AS pollutantCode, po.name AS pollutantName,
                       q.issue_type AS issueType, q.severity, q.detail, q.status,
                       FORMATDATETIME(q.created_at,'yyyy-MM-dd HH:mm') AS createdAt
                FROM data_quality_issue q
                LEFT JOIN enterprise e ON e.id=q.enterprise_id
                LEFT JOIN monitor_point p ON p.id=q.point_id
                LEFT JOIN pollutant po ON po.code=q.pollutant_code
                WHERE q.monitor_date >= ? AND q.monitor_date <= ?
                """);
        List<Object> args = new ArrayList<>();
        args.add(Date.valueOf(start));
        args.add(Date.valueOf(end));
        if (issueType != null && !issueType.isBlank()) {
            sql.append(" AND q.issue_type=?");
            args.add(issueType);
        }
        if (severity != null && !severity.isBlank()) {
            sql.append(" AND q.severity=?");
            args.add(severity);
        }
        sql.append(" ORDER BY q.monitor_date DESC, q.severity DESC, q.point_id LIMIT 500");
        return jdbc.queryForList(sql.toString(), args.toArray());
    }

    public Map<String, Object> summary(LocalDate start, LocalDate end) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", jdbc.queryForObject("""
                SELECT COUNT(*) FROM data_quality_issue WHERE monitor_date >= ? AND monitor_date <= ?
                """, Integer.class, Date.valueOf(start), Date.valueOf(end)));
        result.put("error", jdbc.queryForObject("""
                SELECT COUNT(*) FROM data_quality_issue WHERE monitor_date >= ? AND monitor_date <= ? AND severity='ERROR'
                """, Integer.class, Date.valueOf(start), Date.valueOf(end)));
        result.put("warn", jdbc.queryForObject("""
                SELECT COUNT(*) FROM data_quality_issue WHERE monitor_date >= ? AND monitor_date <= ? AND severity='WARN'
                """, Integer.class, Date.valueOf(start), Date.valueOf(end)));
        result.put("byType", jdbc.queryForList("""
                SELECT issue_type AS issueType, COUNT(*) AS count FROM data_quality_issue
                WHERE monitor_date >= ? AND monitor_date <= ? GROUP BY issue_type ORDER BY count DESC
                """, Date.valueOf(start), Date.valueOf(end)));
        result.put("byPoint", jdbc.queryForList("""
                SELECT point_id AS pointId, COUNT(*) AS count FROM data_quality_issue
                WHERE monitor_date >= ? AND monitor_date <= ? GROUP BY point_id ORDER BY count DESC LIMIT 10
                """, Date.valueOf(start), Date.valueOf(end)));
        return result;
    }
}
