package com.fixedsource.controller;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Read-only analytics for hourly/daily monitoring presentations.
 * Keeps the existing trend endpoint untouched so the enterprise profile stays lightweight.
 */
@RestController
public class MonitoringController {

    private final JdbcTemplate jdbc;

    public MonitoringController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/api/monitoring/analysis")
    public Map<String, Object> analysis(@RequestParam String enterpriseId,
                                        @RequestParam(required = false) String pointId,
                                        @RequestParam(required = false) String pollutantCode,
                                        @RequestParam(defaultValue = "7") int days) {
        int windowDays = Math.min(30, Math.max(1, days));
        Map<String, Object> result = emptyResult(enterpriseId, windowDays);

        List<Map<String, Object>> points = jdbc.queryForList("""
                SELECT id, point_name AS pointName, point_type AS pointType
                FROM monitor_point WHERE enterprise_id=? ORDER BY id
                """, enterpriseId);
        result.put("points", points);
        if (points.isEmpty()) return result;

        String pid = pointId;
        boolean validPoint = false;
        if (pid != null && !pid.isBlank()) {
            for (Map<String, Object> point : points) {
                if (pid.equals(String.valueOf(point.get("id")))) {
                    validPoint = true;
                    break;
                }
            }
        }
        if (!validPoint) pid = String.valueOf(points.get(0).get("id"));
        result.put("pointId", pid);

        List<Map<String, Object>> pollutants = jdbc.queryForList("""
                SELECT DISTINCT po.code, po.name, po.unit,
                       po.limit_value AS limitValue, po.standard_ref AS standardRef
                FROM monitoring_record mr JOIN pollutant po ON po.code=mr.pollutant_code
                WHERE mr.enterprise_id=? AND mr.point_id=? ORDER BY po.code
                """, enterpriseId, pid);
        result.put("pollutants", pollutants);
        if (pollutants.isEmpty()) return result;

        String code = pollutantCode;
        boolean validPollutant = false;
        if (code != null && !code.isBlank()) {
            for (Map<String, Object> item : pollutants) {
                if (code.equals(String.valueOf(item.get("code")))) {
                    validPollutant = true;
                    break;
                }
            }
        }
        if (!validPollutant) code = String.valueOf(pollutants.get(0).get("code"));
        result.put("pollutantCode", code);

        List<Map<String, Object>> meta = jdbc.queryForList("""
                SELECT code, name, unit, limit_value AS limitValue, standard_ref AS standardRef
                FROM pollutant WHERE code=?
                """, code);
        if (meta.isEmpty()) return result;
        Map<String, Object> pollutant = new LinkedHashMap<>(meta.get(0));
        result.put("pollutant", pollutant);
        double limit = number(pick(pollutant, "limitValue", "LIMITVALUE", "limit_value", "LIMIT_VALUE"));

        List<Map<String, Object>> maxRows = jdbc.queryForList("""
                SELECT FORMATDATETIME(MAX(monitor_time),'yyyy-MM-dd') AS latestDate
                FROM monitoring_record
                WHERE enterprise_id=? AND point_id=? AND pollutant_code=?
                """, enterpriseId, pid, code);
        Object latestValue = maxRows.isEmpty() ? null : pick(maxRows.get(0), "latestDate", "LATESTDATE");
        if (latestValue == null) return result;

        LocalDate latestDate = LocalDate.parse(String.valueOf(latestValue));
        LocalDate startDate = latestDate.minusDays(windowDays - 1L);
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT FORMATDATETIME(monitor_time,'yyyy-MM-dd') AS bizDate,
                       FORMATDATETIME(monitor_time,'yyyy-MM-dd HH:mm') AS sampleTime,
                       measured_value AS measuredValue, data_status AS dataStatus
                FROM monitoring_record
                WHERE enterprise_id=? AND point_id=? AND pollutant_code=?
                  AND monitor_time >= ? AND monitor_time < ?
                ORDER BY monitor_time
                """, enterpriseId, pid, code,
                Timestamp.valueOf(startDate.atStartOfDay()),
                Timestamp.valueOf(latestDate.plusDays(1).atStartOfDay()));

        result.put("latestDate", latestDate.toString());
        result.put("startDate", startDate.toString());
        result.put("rowCount", rows.size());

        Map<String, List<Map<String, Object>>> byDate = new LinkedHashMap<>();
        List<Map<String, Object>> normalizedRows = new ArrayList<>();
        for (Map<String, Object> raw : rows) {
            String sampleTime = String.valueOf(pick(raw, "sampleTime", "SAMPLETIME"));
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("bizDate", pick(raw, "bizDate", "BIZDATE"));
            row.put("time", sampleTime);
            row.put("hour", Integer.parseInt(sampleTime.substring(11, 13)));
            row.put("value", number(pick(raw, "measuredValue", "MEASUREDVALUE")));
            row.put("status", pick(raw, "dataStatus", "DATASTATUS"));
            byDate.computeIfAbsent(String.valueOf(row.get("bizDate")), key -> new ArrayList<>()).add(row);
            normalizedRows.add(row);
        }

        List<Map<String, Object>> daily = new ArrayList<>();
        int totalSamples = 0;
        double total = 0;
        double periodMax = Double.NEGATIVE_INFINITY;
        double periodMin = Double.POSITIVE_INFINITY;
        int totalExceed = 0;
        int totalAbnormal = 0;
        String peakDate = null;

        for (LocalDate d = startDate; !d.isAfter(latestDate); d = d.plusDays(1)) {
            String key = d.toString();
            List<Map<String, Object>> dayRows = byDate.getOrDefault(key, Collections.emptyList());
            double daySum = 0;
            double dayMax = Double.NEGATIVE_INFINITY;
            double dayMin = Double.POSITIVE_INFINITY;
            int dayExceed = 0;
            int dayAbnormal = 0;
            for (Map<String, Object> row : dayRows) {
                double value = number(row.get("value"));
                daySum += value;
                dayMax = Math.max(dayMax, value);
                dayMin = Math.min(dayMin, value);
                if (limit > 0 && value > limit) dayExceed++;
                                if (!"NORMAL".equals(String.valueOf(row.getOrDefault("status", "NORMAL")))) dayAbnormal++;
            }
            int sampleCount = dayRows.size();
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("date", key);
            item.put("sampleCount", sampleCount);
            item.put("avg", sampleCount == 0 ? null : round(daySum / sampleCount));
            item.put("max", sampleCount == 0 ? null : round(dayMax));
            item.put("min", sampleCount == 0 ? null : round(dayMin));
            item.put("exceedHours", dayExceed);
            item.put("abnormalCount", dayAbnormal);
            item.put("completeness", Math.min(100, Math.round(sampleCount * 100.0 / 24.0)));
            item.put("status", sampleCount == 0 ? "NO_DATA" : dayExceed > 0 ? "EXCEED" : "NORMAL");
            daily.add(item);

            totalSamples += sampleCount;
            total += daySum;
            if (sampleCount > 0) {
                if (dayMax > periodMax) {
                    periodMax = dayMax;
                    peakDate = key;
                }
                periodMin = Math.min(periodMin, dayMin);
            }
            totalExceed += dayExceed;
            totalAbnormal += dayAbnormal;
        }

        Map<Integer, Map<String, Object>> latestByHour = new HashMap<>();
        for (Map<String, Object> row : byDate.getOrDefault(latestDate.toString(), Collections.emptyList())) {
            latestByHour.put((Integer) row.get("hour"), row);
        }
        List<Map<String, Object>> hourly = new ArrayList<>();
        for (int hour = 0; hour < 24; hour++) {
            Map<String, Object> row = latestByHour.get(hour);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("time", latestDate + " " + String.format("%02d:00", hour));
            item.put("hour", hour);
            if (row == null) {
                item.put("value", null);
                item.put("status", "MISSING");
                item.put("exceed", false);
            } else {
                double value = number(row.get("value"));
                item.put("value", value);
                item.put("status", row.get("status"));
                item.put("exceed", limit > 0 && value > limit);
            }
            hourly.add(item);
        }

        int heatDays = Math.min(7, windowDays);
        LocalDate heatStart = latestDate.minusDays(heatDays - 1L);
        Set<String> heatDates = new LinkedHashSet<>();
        List<Map<String, Object>> heatmap = new ArrayList<>();
        for (LocalDate d = heatStart; !d.isAfter(latestDate); d = d.plusDays(1)) {
            String key = d.toString();
            heatDates.add(key);
            for (Map<String, Object> row : byDate.getOrDefault(key, Collections.emptyList())) {
                Map<String, Object> cell = new LinkedHashMap<>();
                cell.put("date", key);
                cell.put("hour", row.get("hour"));
                cell.put("value", row.get("value"));
                cell.put("status", row.get("status"));
                heatmap.add(cell);
            }
        }

        List<Map<String, Object>> profile = new ArrayList<>();
        for (int hour = 0; hour < 24; hour++) {
            double sum = 0;
            double max = Double.NEGATIVE_INFINITY;
            int count = 0;
            for (Map<String, Object> row : normalizedRows) {
                if (Integer.parseInt(String.valueOf(row.get("hour"))) != hour) continue;
                double value = number(row.get("value"));
                sum += value;
                max = Math.max(max, value);
                count++;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("hour", hour);
            item.put("avg", count == 0 ? null : round(sum / count));
            item.put("max", count == 0 ? null : round(max));
            item.put("count", count);
            profile.add(item);
        }

        Map<String, Object> latestDaily = daily.isEmpty() ? Map.of() : daily.get(daily.size() - 1);
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("sampleCount", totalSamples);
        stats.put("expectedSamples", windowDays * 24);
        stats.put("completeness", Math.min(100, Math.round(totalSamples * 100.0 / (windowDays * 24.0))));
        stats.put("avg", totalSamples == 0 ? null : round(total / totalSamples));
        stats.put("max", totalSamples == 0 ? null : round(periodMax));
        stats.put("min", totalSamples == 0 ? null : round(periodMin));
        stats.put("exceedHours", totalExceed);
        stats.put("abnormalHours", totalAbnormal);
        stats.put("peakDate", peakDate);
        stats.put("latestAvg", latestDaily.get("avg"));
        stats.put("latestMax", latestDaily.get("max"));
        stats.put("latestCompleteness", latestDaily.get("completeness"));
        stats.put("latestExceedHours", latestDaily.get("exceedHours"));

        result.put("daily", daily);
        result.put("hourly", hourly);
        result.put("heatmap", heatmap);
        result.put("heatDates", new ArrayList<>(heatDates));
        result.put("profile", profile);
        result.put("stats", stats);
        return result;
    }

    private Map<String, Object> emptyResult(String enterpriseId, int days) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("enterpriseId", enterpriseId);
        result.put("days", days);
        result.put("points", List.of());
        result.put("pollutants", List.of());
        result.put("daily", List.of());
        result.put("hourly", List.of());
        result.put("heatmap", List.of());
        result.put("heatDates", List.of());
        result.put("profile", List.of());
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("sampleCount", 0);
        stats.put("expectedSamples", days * 24);
        stats.put("completeness", 0);
        stats.put("exceedHours", 0);
        stats.put("abnormalHours", 0);
        stats.put("avg", null);
        stats.put("max", null);
        stats.put("min", null);
        stats.put("latestAvg", null);
        stats.put("latestMax", null);
        stats.put("latestCompleteness", 0);
        stats.put("latestExceedHours", 0);
        result.put("stats", stats);
        return result;
    }

    private Object pick(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            if (map.containsKey(key) && map.get(key) != null) return map.get(key);
        }
        return null;
    }

    private double number(Object value) {
        if (value == null) return 0;
        if (value instanceof Number n) return n.doubleValue();
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private double round(double value) {
        return Math.round(value * 10000.0) / 10000.0;
    }
}
