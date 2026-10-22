package com.fixedsource.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class BacktestService {

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public BacktestService(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> evaluate(LocalDate start, LocalDate end) {
        List<Map<String, Object>> labels = jdbc.queryForList("""
                SELECT rule_code AS ruleCode, point_id AS pointId, pollutant_code AS pollutantCode,
                       FORMATDATETIME(biz_date,'yyyy-MM-dd') AS bizDate, COUNT(*) AS count
                FROM test_anomaly_label WHERE biz_date >= ? AND biz_date <= ?
                GROUP BY rule_code, point_id, pollutant_code, biz_date
                """, Date.valueOf(start), Date.valueOf(end));
        List<Map<String, Object>> detected = jdbc.queryForList("""
                SELECT rule_code AS ruleCode, point_id AS pointId, pollutant_code AS pollutantCode,
                       FORMATDATETIME(CAST(start_time AS DATE),'yyyy-MM-dd') AS bizDate, COUNT(*) AS count
                FROM clue WHERE start_time >= ? AND start_time < ? AND rule_code LIKE 'R%'
                GROUP BY rule_code, point_id, pollutant_code, CAST(start_time AS DATE)
                """, Timestamp.valueOf(start.atStartOfDay()), Timestamp.valueOf(end.plusDays(1).atStartOfDay()));

        Map<String, Integer> labelMap = new LinkedHashMap<>();
        Map<String, Integer> detectedMap = new LinkedHashMap<>();
        for (Map<String, Object> row : labels) {
            labelMap.merge(key(row), ((Number) row.get("count")).intValue(), Integer::sum);
        }
        for (Map<String, Object> row : detected) {
            detectedMap.merge(key(row), ((Number) row.get("count")).intValue(), Integer::sum);
        }
        Map<String, String> ruleNames = new LinkedHashMap<>();
        for (Map<String, Object> row : jdbc.queryForList("SELECT rule_code AS ruleCode, name FROM rule_config")) {
            ruleNames.put(String.valueOf(row.get("ruleCode")), String.valueOf(row.get("name")));
        }
        Map<String, int[]> stats = new LinkedHashMap<>();
        java.util.Set<String> keys = new java.util.LinkedHashSet<>();
        keys.addAll(labelMap.keySet());
        keys.addAll(detectedMap.keySet());
        for (String key : keys) {
            String ruleCode = key.split("\\|")[0];
            int labelCount = labelMap.getOrDefault(key, 0);
            int detectedCount = detectedMap.getOrDefault(key, 0);
            int tp = Math.min(labelCount, detectedCount);
            int fp = Math.max(0, detectedCount - labelCount);
            int fn = Math.max(0, labelCount - detectedCount);
            int[] s = stats.computeIfAbsent(ruleCode, k -> new int[5]);
            s[0] += tp;
            s[1] += fp;
            s[2] += fn;
            s[3] += labelCount;
            s[4] += detectedCount;
        }
        int[] total = new int[5];
        List<Map<String, Object>> byRule = new ArrayList<>();
        stats.entrySet().stream()
                .sorted(Comparator.comparing(Map.Entry::getKey))
                .forEach(entry -> {
                    int[] s = entry.getValue();
                    for (int i = 0; i < 5; i++) total[i] += s[i];
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("ruleCode", entry.getKey());
                    row.put("ruleName", ruleNames.getOrDefault(entry.getKey(), entry.getKey()));
                    row.put("labels", s[3]);
                    row.put("detected", s[4]);
                    row.put("tp", s[0]);
                    row.put("fp", s[1]);
                    row.put("fn", s[2]);
                    row.put("precision", round(ratio(s[0], s[0] + s[1])));
                    row.put("recall", round(ratio(s[0], s[0] + s[2])));
                    row.put("f1", round(f1(s[0], s[1], s[2])));
                    byRule.add(row);
                });
        Map<String, Object> overall = new LinkedHashMap<>();
        overall.put("labels", total[3]);
        overall.put("detected", total[4]);
        overall.put("tp", total[0]);
        overall.put("fp", total[1]);
        overall.put("fn", total[2]);
        overall.put("precision", round(ratio(total[0], total[0] + total[1])));
        overall.put("recall", round(ratio(total[0], total[0] + total[2])));
        overall.put("f1", round(f1(total[0], total[1], total[2])));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("startDate", start.toString());
        result.put("endDate", end.toString());
        result.put("overall", overall);
        result.put("byRule", byRule);
        result.put("matchMethod", "按规则+点位+污染物+日期分组计数匹配，TP=min(标签数,命中数)");
        result.put("note", "这是原型回测口径，时间边界和状态排除的细粒度匹配可在后续版本加强。");
        return result;
    }

    public Map<String, Object> run(LocalDate start, LocalDate end, String operator) {
        Map<String, Object> result = evaluate(start, end);
        String runNo = "BT" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + String.format("%03d", ThreadLocalRandom.current().nextInt(1000));
        Map<String, Object> overall = (Map<String, Object>) result.get("overall");
        try {
            jdbc.update("""
                    INSERT INTO backtest_run(run_no,start_date,end_date,rule_version,total_labels,detected_count,
                        true_positive,false_positive,false_negative,precision_value,recall_value,f1_value,metrics_json,created_by)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                    """, runNo, Date.valueOf(start), Date.valueOf(end), "R001-R005/v1.0",
                    num(overall.get("labels")), num(overall.get("detected")), num(overall.get("tp")),
                    num(overall.get("fp")), num(overall.get("fn")), dbl(overall.get("precision")),
                    dbl(overall.get("recall")), dbl(overall.get("f1")),
                    objectMapper.writeValueAsString(result), operator);
        } catch (Exception e) {
            throw new IllegalStateException("保存回测结果失败: " + e.getMessage(), e);
        }
        result.put("id", jdbc.queryForObject("SELECT id FROM backtest_run WHERE run_no=?", Long.class, runNo));
        result.put("runNo", runNo);
        result.put("createdBy", operator);
        return result;
    }

    public List<Map<String, Object>> listRuns(int limit) {
        return jdbc.queryForList("""
                SELECT id, run_no AS runNo, FORMATDATETIME(start_date,'yyyy-MM-dd') AS startDate,
                       FORMATDATETIME(end_date,'yyyy-MM-dd') AS endDate, rule_version AS ruleVersion,
                       total_labels AS totalLabels, detected_count AS detectedCount,
                       true_positive AS truePositive, false_positive AS falsePositive, false_negative AS falseNegative,
                       precision_value AS precision, recall_value AS recall, f1_value AS f1,
                       created_by AS createdBy, FORMATDATETIME(created_at,'yyyy-MM-dd HH:mm') AS createdAt
                FROM backtest_run ORDER BY id DESC LIMIT ?
                """, limit);
    }

    public Map<String, Object> getRun(long id) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT id, run_no AS runNo, FORMATDATETIME(start_date,'yyyy-MM-dd') AS startDate,
                       FORMATDATETIME(end_date,'yyyy-MM-dd') AS endDate, rule_version AS ruleVersion,
                       total_labels AS totalLabels, detected_count AS detectedCount,
                       true_positive AS truePositive, false_positive AS falsePositive, false_negative AS falseNegative,
                       precision_value AS precision, recall_value AS recall, f1_value AS f1,
                       metrics_json AS metricsJson, created_by AS createdBy,
                       FORMATDATETIME(created_at,'yyyy-MM-dd HH:mm') AS createdAt
                FROM backtest_run WHERE id=?
                """, id);
        if (rows.isEmpty()) return null;
        Map<String, Object> run = rows.get(0);
        try {
            run.put("metrics", objectMapper.readValue(String.valueOf(run.remove("metricsJson")), new TypeReference<>() {}));
        } catch (Exception ignored) {
            run.put("metrics", Map.of());
        }
        return run;
    }

    private String key(Map<String, Object> row) {
        return row.get("ruleCode") + "|" + row.get("pointId") + "|" + row.get("pollutantCode") + "|" + row.get("bizDate");
    }

    private double ratio(int a, int b) {
        return b == 0 ? 0 : a * 1.0 / b;
    }

    private double f1(int tp, int fp, int fn) {
        double p = ratio(tp, tp + fp);
        double r = ratio(tp, tp + fn);
        return p + r == 0 ? 0 : 2 * p * r / (p + r);
    }

    private double round(double v) {
        return Math.round(v * 10000.0) / 10000.0;
    }

    private int num(Object value) {
        return value == null ? 0 : ((Number) value).intValue();
    }

    private double dbl(Object value) {
        return value == null ? 0 : ((Number) value).doubleValue();
    }
}
