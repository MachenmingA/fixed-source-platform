package com.fixedsource.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class RuleEngineService {

    private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("HH:mm");
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public RuleEngineService(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    private record Rec(LocalDateTime time, double value, String status) {}
    private record ClueDraft(String ruleCode, String ruleName, String severity, LocalDateTime start, LocalDateTime end,
                             double trigger, double threshold, List<String> evidence, String summary) {}

    public int runForDate(LocalDate date, String batchNo) {
        Set<String> enabled = new HashSet<>(jdbc.queryForList(
                "SELECT rule_code FROM rule_config WHERE enabled=TRUE", String.class));
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT point_id AS pointId, pollutant_code AS pollutantCode, monitor_time AS monitorTime,
                       measured_value AS measuredValue, data_status AS dataStatus
                FROM monitoring_record WHERE monitor_time >= ? AND monitor_time < ?
                ORDER BY point_id, pollutant_code, monitor_time
                """, Timestamp.valueOf(date.atStartOfDay()), Timestamp.valueOf(date.plusDays(1).atStartOfDay()));
        Map<String, List<Rec>> grouped = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String key = row.get("pointId") + "|" + row.get("pollutantCode");
            Timestamp ts = (Timestamp) row.get("monitorTime");
            grouped.computeIfAbsent(key, k -> new ArrayList<>())
                    .add(new Rec(ts.toLocalDateTime(), ((Number) row.get("measuredValue")).doubleValue(),
                            String.valueOf(row.get("dataStatus"))));
        }
        int before = countClues();
        for (MockSourceService.PointDef point : MockSourceService.POINTS) {
            for (String pollutantCode : point.pollutants()) {
                List<Rec> recs = grouped.getOrDefault(point.id() + "|" + pollutantCode, List.of());
                runRules(point, pollutantCode, date, recs, enabled, batchNo);
            }
        }
        return countClues() - before;
    }

    public int runForDateRange(LocalDate start, LocalDate end, String batchNo) {
        int count = 0;
        LocalDate d = start;
        while (!d.isAfter(end)) {
            count += runForDate(d, batchNo);
            d = d.plusDays(1);
        }
        return count;
    }

    private void runRules(MockSourceService.PointDef point, String pollutantCode, LocalDate date,
                          List<Rec> recs, Set<String> enabled, String batchNo) {
        MockSourceService.PollutantDef pollutant = MockSourceService.pollutant(pollutantCode);
        if (pollutant == null) return;
        double limit = pollutant.limit();
        int completeness = Math.min(100, recs.size() * 100 / 24);

        if (enabled.contains("R001")) {
            int i = 0;
            while (i < recs.size()) {
                Rec r = recs.get(i);
                if (r.value() > limit) {
                    int j = i;
                    double max = r.value();
                    List<String> evidence = new ArrayList<>();
                    while (j < recs.size() && recs.get(j).value() > limit
                            && (j == i || Duration.between(recs.get(j - 1).time(), recs.get(j).time()).toMinutes() <= 60)) {
                        max = Math.max(max, recs.get(j).value());
                        if (evidence.size() < 10) {
                            evidence.add(recs.get(j).time().format(HM) + " 实测 " + fmt(recs.get(j).value()) + "，限值 " + fmt(limit));
                        }
                        j++;
                    }
                    LocalDateTime end = recs.get(j - 1).time();
                    String severity = max >= limit * 1.3 ? "HIGH" : "MEDIUM";
                    saveClue(point, pollutantCode, date, completeness,
                            new ClueDraft("R001", "浓度超限", severity, r.time(), end, max, limit, evidence,
                                    pollutant.name() + " 浓度超限"), batchNo);
                    i = j;
                } else {
                    i++;
                }
            }
        }

        if (enabled.contains("R002") && recs.size() < 22) {
            Set<Integer> present = new HashSet<>();
            for (Rec r : recs) present.add(r.time().getHour());
            List<Integer> missingHours = new ArrayList<>();
            for (int h = 0; h < 24; h++) if (!present.contains(h)) missingHours.add(h);
            if (missingHours.size() >= 3) {
                List<String> evidence = new ArrayList<>();
                evidence.add("应到 24 条，实到 " + recs.size() + " 条，缺失 " + missingHours.size() + " 小时");
                for (int h : missingHours.subList(0, Math.min(12, missingHours.size()))) {
                    evidence.add(String.format("%02d:00 无有效数据", h));
                }
                LocalDateTime start = date.atTime(missingHours.get(0), 0);
                LocalDateTime end = date.atTime(missingHours.get(missingHours.size() - 1), 0);
                String severity = missingHours.size() >= 6 ? "MEDIUM" : "LOW";
                saveClue(point, pollutantCode, date, completeness,
                        new ClueDraft("R002", "数据缺失", severity, start, end, missingHours.size(), 3, evidence,
                                pollutant.name() + " 数据缺失 " + missingHours.size() + " 小时"), batchNo);
            }
        }

        if (enabled.contains("R003")) {
            int i = 0;
            while (i < recs.size()) {
                long key = Math.round(recs.get(i).value() * 100);
                if (key == 0) { i++; continue; }
                int j = i;
                while (j + 1 < recs.size() && Math.round(recs.get(j + 1).value() * 100) == key
                        && Duration.between(recs.get(j).time(), recs.get(j + 1).time()).toMinutes() <= 60) {
                    j++;
                }
                if (j - i + 1 >= 6) {
                    List<String> evidence = new ArrayList<>();
                    evidence.add(recs.get(i).time().format(HM) + " 至 " + recs.get(j).time().format(HM)
                            + " 连续 " + (j - i + 1) + " 小时保持 " + fmt(recs.get(i).value()));
                    saveClue(point, pollutantCode, date, completeness,
                            new ClueDraft("R003", "连续恒值", "MEDIUM", recs.get(i).time(), recs.get(j).time(),
                                    recs.get(i).value(), 6, evidence, pollutant.name() + " 连续恒值"), batchNo);
                }
                i = j + 1;
            }
        }

        if (enabled.contains("R004")) {
            boolean[] constantIndex = new boolean[recs.size()];
            int ci = 0;
            while (ci < recs.size()) {
                long key = Math.round(recs.get(ci).value() * 100);
                if (key == 0) { ci++; continue; }
                int cj = ci;
                while (cj + 1 < recs.size() && Math.round(recs.get(cj + 1).value() * 100) == key
                        && Duration.between(recs.get(cj).time(), recs.get(cj + 1).time()).toMinutes() <= 60) {
                    cj++;
                }
                if (cj - ci + 1 >= 6) {
                    for (int k = ci; k <= cj; k++) constantIndex[k] = true;
                }
                ci = cj + 1;
            }
            Set<String> badStatus = Set.of("FAULT", "STOP", "CALIBRATION", "MAINTENANCE");
            for (int i = 1; i < recs.size(); i++) {
                Rec previous = recs.get(i - 1);
                Rec current = recs.get(i);
                if (Duration.between(previous.time(), current.time()).toMinutes() > 60) continue;
                if (previous.value() <= 0) continue;
                boolean related = constantIndex[i - 1] || constantIndex[i]
                        || previous.value() > limit || current.value() > limit
                        || (badStatus.contains(previous.status()) && previous.value() > 0)
                        || (badStatus.contains(current.status()) && current.value() > 0);
                if (related) continue;
                double prev = previous.value();
                double curr = current.value();
                double change = (curr - prev) / prev * 100;
                if (Math.abs(change) >= 100) {
                    List<String> evidence = new ArrayList<>();
                    evidence.add(previous.time().format(HM) + " 值 " + fmt(prev));
                    evidence.add(current.time().format(HM) + " 值 " + fmt(curr) + "，变化 " + fmt(change) + "%");
                    saveClue(point, pollutantCode, date, completeness,
                            new ClueDraft("R004", "异常突变", "MEDIUM", previous.time(), current.time(),
                                    Math.abs(change), 100, evidence, pollutant.name() + " 异常突变 " + fmt(change) + "%"), batchNo);
                }
            }
        }

        if (enabled.contains("R005")) {
            Set<String> badStatus = Set.of("FAULT", "STOP", "CALIBRATION", "MAINTENANCE");
            int i = 0;
            while (i < recs.size()) {
                Rec r = recs.get(i);
                if (badStatus.contains(r.status()) && r.value() > 0) {
                    int j = i;
                    double max = r.value();
                    List<String> evidence = new ArrayList<>();
                    while (j < recs.size() && recs.get(j).status().equals(r.status()) && recs.get(j).value() > 0
                            && (j == i || Duration.between(recs.get(j - 1).time(), recs.get(j).time()).toMinutes() <= 60)) {
                        max = Math.max(max, recs.get(j).value());
                        if (evidence.size() < 8) {
                            evidence.add(recs.get(j).time().format(HM) + " 状态 " + recs.get(j).status()
                                    + "，实测 " + fmt(recs.get(j).value()));
                        }
                        j++;
                    }
                    saveClue(point, pollutantCode, date, completeness,
                            new ClueDraft("R005", "状态不一致", "HIGH", r.time(), recs.get(j - 1).time(), max, 0,
                                    evidence, pollutant.name() + " 状态与排放矛盾"), batchNo);
                    i = j;
                } else {
                    i++;
                }
            }
        }
    }

    private void saveClue(MockSourceService.PointDef point, String pollutantCode, LocalDate date, int completeness,
                          ClueDraft draft, String batchNo) {
        String fingerprint = draft.ruleCode() + "|" + point.id() + "|" + pollutantCode + "|" + draft.start();
        Integer exists = jdbc.queryForObject("SELECT COUNT(*) FROM clue WHERE fingerprint=?", Integer.class, fingerprint);
        if (exists != null && exists > 0) return;
        int certainty = switch (draft.ruleCode()) {
            case "R001" -> 95;
            case "R005" -> 92;
            case "R002" -> 82;
            case "R003" -> 78;
            default -> 72;
        };
        int credibility = Math.max(20, Math.min(98,
                (int) Math.round(certainty * 0.45 + completeness * 0.35 + 60 * 0.20)));
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("ruleCode", draft.ruleCode());
        evidence.put("ruleName", draft.ruleName());
        evidence.put("summary", draft.summary());
        evidence.put("pointId", point.id());
        evidence.put("pointName", point.pointName());
        evidence.put("outletName", point.outletName());
        evidence.put("pollutant", pollutantCode);
        evidence.put("limit", MockSourceService.pollutant(pollutantCode).limit());
        evidence.put("values", draft.evidence());
        Map<String, Object> credibilityJson = new LinkedHashMap<>();
        credibilityJson.put("dataCompleteness", completeness);
        credibilityJson.put("ruleCertainty", certainty);
        credibilityJson.put("historyConsistency", 60);
        credibilityJson.put("version", "cred-v1");
        String clueNo = "CL" + date.format(DateTimeFormatter.BASIC_ISO_DATE)
                + String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
        try {
            jdbc.update("""
                    INSERT INTO clue(clue_no,enterprise_id,point_id,pollutant_code,rule_code,rule_name,severity,
                        start_time,end_time,trigger_value,threshold_value,evidence_json,fingerprint,batch_no,status,
                        source,credibility,credibility_json,ai_status)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                    """, clueNo, point.enterpriseId(), point.id(), pollutantCode, draft.ruleCode(), draft.ruleName(),
                    draft.severity(), Timestamp.valueOf(draft.start()), Timestamp.valueOf(draft.end()),
                    draft.trigger(), draft.threshold(), objectMapper.writeValueAsString(evidence), fingerprint,
                    batchNo, "PENDING", "AUTO", credibility, objectMapper.writeValueAsString(credibilityJson), "NONE");
        } catch (Exception e) {
            throw new IllegalStateException("保存线索失败: " + e.getMessage(), e);
        }
    }

    private int countClues() {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM clue", Integer.class);
        return n == null ? 0 : n;
    }

    private String fmt(double v) {
        return String.format("%.2f", v);
    }
}
