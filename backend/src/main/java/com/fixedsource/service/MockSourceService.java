package com.fixedsource.service;

import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

@Service
public class MockSourceService {

    public record EnterpriseDef(String id, String name, String industry, String region, String level, String creditCode) {}
    public record PointDef(String id, String enterpriseId, String outletName, String pointName, String type, List<String> pollutants) {}
    public record PollutantDef(String code, String name, String unit, double limit, String standardRef) {}
    public record MonitoringRow(String enterpriseId, String pointId, String pollutantCode, LocalDateTime time,
                                double value, double converted, double flow, String status) {}
    public record Anomaly(String type, int startHour, int endHour, double value, String status) {}

    public static final List<EnterpriseDef> ENTERPRISES = List.of(
            new EnterpriseDef("E001", "华辰热电有限公司", "电力热力", "海淀区", "重点", "91110108MA01HC001"),
            new EnterpriseDef("E002", "北辰化工有限公司", "化工", "朝阳区", "重点", "91110105MA01BC002"),
            new EnterpriseDef("E003", "京北制药股份有限公司", "医药制造", "昌平区", "一般", "91110114MA01JB003"),
            new EnterpriseDef("E004", "通达造纸有限公司", "造纸", "通州区", "重点", "91110112MA01TD004"),
            new EnterpriseDef("E005", "绿源食品有限公司", "食品加工", "丰台区", "一般", "91110106MA01LY005"),
            new EnterpriseDef("E006", "恒信表面涂装有限公司", "表面涂装", "大兴区", "重点", "91110115MA01HX006"),
            new EnterpriseDef("E007", "燕山新材料有限公司", "建材", "房山区", "重点", "91110111MA01YS007"),
            new EnterpriseDef("E008", "清源水务有限公司", "污水处理", "顺义区", "重点", "91110113MA01QY008"),
            new EnterpriseDef("E009", "瑞康生物科技有限公司", "医药制造", "海淀区", "一般", "91110108MA01RK009"),
            new EnterpriseDef("E010", "金桥印刷有限公司", "印刷", "石景山区", "一般", "91110107MA01JQ010")
    );

    public static final List<PointDef> POINTS = List.of(
            new PointDef("P001-1", "E001", "1号废气排口", "1号机组废气监测点", "废气", List.of("SO2", "NOX", "PM")),
            new PointDef("P001-2", "E001", "2号废气排口", "2号机组废气监测点", "废气", List.of("SO2", "NOX", "PM")),
            new PointDef("P002-1", "E002", "工艺废气排口", "工艺废气监测点", "废气", List.of("SO2", "NOX", "VOC")),
            new PointDef("P002-2", "E002", "锅炉废气排口", "锅炉烟气监测点", "废气", List.of("SO2", "NOX", "PM")),
            new PointDef("P003-1", "E003", "发酵废气排口", "发酵尾气监测点", "废气", List.of("SO2", "NOX", "PM")),
            new PointDef("P003-2", "E003", "污水总排口", "废水总排口监测点", "废水", List.of("COD", "NH3N")),
            new PointDef("P004-1", "E004", "造纸废水总排口", "废水总排口监测点", "废水", List.of("COD", "NH3N")),
            new PointDef("P004-2", "E004", "锅炉废气排口", "锅炉烟气监测点", "废气", List.of("SO2", "NOX", "PM")),
            new PointDef("P005-1", "E005", "食品废水总排口", "废水总排口监测点", "废水", List.of("COD", "NH3N")),
            new PointDef("P005-2", "E005", "燃气锅炉排口", "锅炉烟气监测点", "废气", List.of("NOX", "SO2")),
            new PointDef("P006-1", "E006", "涂装废气排口1", "涂装线1废气监测点", "废气", List.of("VOC", "PM", "SO2")),
            new PointDef("P006-2", "E006", "涂装废气排口2", "涂装线2废气监测点", "废气", List.of("VOC", "PM")),
            new PointDef("P007-1", "E007", "窑炉废气排口", "窑炉烟气监测点", "废气", List.of("SO2", "NOX", "PM")),
            new PointDef("P007-2", "E007", "原料堆场排口", "堆场颗粒物监测点", "废气", List.of("PM", "SO2")),
            new PointDef("P008-1", "E008", "进水监测点", "进水水质监测点", "废水", List.of("COD", "NH3N")),
            new PointDef("P008-2", "E008", "出水总排口", "出水水质监测点", "废水", List.of("COD", "NH3N")),
            new PointDef("P009-1", "E009", "研发废气排口", "研发楼废气监测点", "废气", List.of("VOC", "NOX")),
            new PointDef("P009-2", "E009", "污水总排口", "废水总排口监测点", "废水", List.of("COD", "NH3N")),
            new PointDef("P010-1", "E010", "印刷废气排口", "印刷车间废气监测点", "废气", List.of("VOC", "PM")),
            new PointDef("P010-2", "E010", "危废暂存间排口", "暂存间废气监测点", "废气", List.of("VOC", "SO2"))
    );

    public static final List<PollutantDef> POLLUTANTS = List.of(
            new PollutantDef("SO2", "二氧化硫", "mg/m³", 50, "GB 16297-1996"),
            new PollutantDef("NOX", "氮氧化物", "mg/m³", 100, "GB 16297-1996"),
            new PollutantDef("PM", "颗粒物", "mg/m³", 20, "GB 16297-1996"),
            new PollutantDef("COD", "化学需氧量", "mg/L", 500, "GB 8978-1996"),
            new PollutantDef("NH3N", "氨氮", "mg/L", 45, "GB 8978-1996"),
            new PollutantDef("VOC", "挥发性有机物", "mg/m³", 60, "DB11/ 1227-2015")
    );

    private static final Map<String, PointDef> POINT_MAP = new LinkedHashMap<>();
    private static final Map<String, PollutantDef> POLLUTANT_MAP = new LinkedHashMap<>();
    private static final Map<String, Anomaly> ANOMALIES = new LinkedHashMap<>();

    static {
        for (PointDef p : POINTS) POINT_MAP.put(p.id(), p);
        for (PollutantDef p : POLLUTANTS) POLLUTANT_MAP.put(p.code(), p);

        anom(1, "P001-1", "SO2", "EXCEED", 10, 13, 90, "NORMAL");
        anom(1, "P002-1", "NOX", "MISSING", 8, 13, 0, "NORMAL");
        anom(1, "P003-2", "COD", "CONSTANT", 2, 9, 88, "NORMAL");
        anom(1, "P004-2", "SO2", "JUMP", 15, 15, 48, "NORMAL");
        anom(1, "P005-2", "SO2", "STATUS", 5, 7, 40, "FAULT");
        anom(1, "P006-1", "VOC", "EXCEED", 9, 12, 95, "NORMAL");

        anom(2, "P004-1", "COD", "CONSTANT", 0, 7, 120, "NORMAL");
        anom(2, "P007-1", "NOX", "EXCEED", 18, 20, 180, "NORMAL");
        anom(2, "P009-1", "VOC", "MISSING", 14, 17, 0, "NORMAL");
        anom(2, "P010-2", "SO2", "EXCEED", 8, 10, 80, "NORMAL");

        anom(3, "P002-2", "SO2", "EXCEED", 6, 9, 75, "NORMAL");
        anom(3, "P007-2", "SO2", "JUMP", 12, 12, 48, "NORMAL");
        anom(3, "P009-2", "NH3N", "CONSTANT", 10, 16, 18, "NORMAL");

        anom(4, "P001-2", "NOX", "EXCEED", 20, 22, 160, "NORMAL");
        anom(4, "P003-1", "PM", "STATUS", 3, 5, 25, "FAULT");
        anom(4, "P005-2", "SO2", "JUMP", 16, 16, 48, "NORMAL");

        anom(5, "P004-1", "COD", "CONSTANT", 8, 15, 110, "NORMAL");
        anom(5, "P005-1", "NH3N", "MISSING", 0, 5, 0, "NORMAL");
        anom(5, "P010-1", "VOC", "EXCEED", 11, 14, 95, "NORMAL");

        anom(6, "P007-1", "SO2", "EXCEED", 9, 12, 85, "NORMAL");
        anom(6, "P008-2", "COD", "CONSTANT", 1, 8, 95, "NORMAL");
        anom(6, "P009-2", "NH3N", "JUMP", 19, 19, 40, "NORMAL");

        anom(7, "P001-1", "PM", "MISSING", 12, 17, 0, "NORMAL");
        anom(7, "P002-2", "NOX", "EXCEED", 7, 10, 150, "NORMAL");
        anom(7, "P003-2", "COD", "CONSTANT", 4, 10, 88, "NORMAL");

        anom(0, "P001-1", "SO2", "EXCEED", 9, 11, 85, "NORMAL");
        anom(0, "P002-1", "NOX", "MISSING", 4, 8, 0, "NORMAL");
        anom(0, "P003-2", "COD", "CONSTANT", 2, 7, 88, "NORMAL");
    }

    private static void anom(int offset, String pointId, String pollutant, String type, int start, int end, double value, String status) {
        ANOMALIES.put(offset + "|" + pointId + "|" + pollutant, new Anomaly(type, start, end, value, status));
    }

    public Map<String, Object> baseDataPayload() {
        List<Map<String, Object>> enterprises = new ArrayList<>();
        for (EnterpriseDef e : ENTERPRISES) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", e.id());
            m.put("name", e.name());
            m.put("industry", e.industry());
            m.put("region", e.region());
            m.put("managementLevel", e.level());
            m.put("creditCode", e.creditCode());
            enterprises.add(m);
        }
        List<Map<String, Object>> points = new ArrayList<>();
        for (PointDef p : POINTS) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", p.id());
            m.put("enterpriseId", p.enterpriseId());
            m.put("outletName", p.outletName());
            m.put("pointName", p.pointName());
            m.put("type", p.type());
            m.put("pollutants", p.pollutants());
            points.add(m);
        }
        List<Map<String, Object>> pollutants = new ArrayList<>();
        for (PollutantDef p : POLLUTANTS) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("code", p.code());
            m.put("name", p.name());
            m.put("unit", p.unit());
            m.put("limit", p.limit());
            m.put("standardRef", p.standardRef());
            pollutants.add(m);
        }
        return Map.of("enterprises", enterprises, "points", points, "pollutants", pollutants);
    }

    public Map<String, Object> monitoringPayload(LocalDate date) {
        List<Map<String, Object>> records = new ArrayList<>();
        for (MonitoringRow r : generateDay(date)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("enterpriseId", r.enterpriseId());
            m.put("pointId", r.pointId());
            m.put("pollutantCode", r.pollutantCode());
            m.put("monitorTime", r.time().toString());
            m.put("measuredValue", r.value());
            m.put("convertedValue", r.converted());
            m.put("flowValue", r.flow());
            m.put("dataStatus", r.status());
            records.add(m);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("bizDate", date.toString());
        result.put("expectedCount", totalExpectedPerDay());
        result.put("receivedCount", records.size());
        result.put("records", records);
        return result;
    }

    public List<MonitoringRow> generateDay(LocalDate date) {
        List<MonitoringRow> rows = new ArrayList<>();
        for (PointDef point : POINTS) {
            for (String pollutantCode : point.pollutants()) {
                PollutantDef pollutant = POLLUTANT_MAP.get(pollutantCode);
                for (int hour = 0; hour < 24; hour++) {
                    Anomaly anomaly = anomalyFor(date, point.id(), pollutantCode, hour);
                    if (anomaly != null && "MISSING".equals(anomaly.type())) {
                        continue;
                    }
                    double value = normalValue(point, pollutantCode, hour, date);
                    String status = "NORMAL";
                    if (anomaly != null) {
                        switch (anomaly.type()) {
                            case "EXCEED", "CONSTANT" -> value = anomaly.value();
                            case "JUMP" -> {
                                if (hour == anomaly.startHour()) value = anomaly.value();
                            }
                            case "STATUS" -> status = anomaly.status();
                            default -> { }
                        }
                    }
                    double flow = point.type().equals("废水")
                            ? round(80 + Math.abs(Math.sin((point.id() + hour).hashCode() % 97)) * 220)
                            : round(2200 + Math.abs(Math.sin((point.id() + hour + date.getDayOfYear()).hashCode() % 89)) * 3800);
                    rows.add(new MonitoringRow(point.enterpriseId(), point.id(), pollutantCode,
                            date.atTime(hour, 0), round(value), round(value * 1.05), flow, status));
                }
            }
        }
        return rows;
    }

    private double normalValue(PointDef point, String pollutantCode, int hour, LocalDate date) {
        PollutantDef p = POLLUTANT_MAP.get(pollutantCode);
        double base = switch (pollutantCode) {
            case "SO2" -> 22;
            case "NOX" -> 55;
            case "PM" -> 10;
            case "COD" -> 180;
            case "NH3N" -> 18;
            case "VOC" -> 32;
            default -> Math.max(1, p.limit() * 0.4);
        };
        int seed = (point.id() + pollutantCode + date.toString() + hour).hashCode();
        Random random = new Random(seed);
        double wave = 0.82 + 0.28 * Math.abs(Math.sin((hour + point.id().charAt(point.id().length() - 1)) * 0.55));
        double noise = (random.nextDouble() - 0.5) * base * 0.12;
        return base * wave + noise;
    }

    private Anomaly anomalyFor(LocalDate date, String pointId, String pollutantCode, int hour) {
        long offset = ChronoUnit.DAYS.between(date, LocalDate.now());
        if (offset < 0 || offset > 7) return null;
        // 以业务日期生成稳定的异常剧本槽位，避免跨天重跑时标签与监测数据错位。
        long slot = Math.floorMod(date.toEpochDay(), 8);
        Anomaly a = ANOMALIES.get(slot + "|" + pointId + "|" + pollutantCode);
        if (a == null) return null;
        return hour >= a.startHour() && hour <= a.endHour() ? a : null;
    }

    public List<Map<String, Object>> labelsForDate(LocalDate date) {
        long offset = ChronoUnit.DAYS.between(date, LocalDate.now());
        List<Map<String, Object>> labels = new ArrayList<>();
        if (offset < 0 || offset > 7) return labels;
        long slot = Math.floorMod(date.toEpochDay(), 8);
        String prefix = slot + "|";
        for (Map.Entry<String, Anomaly> entry : ANOMALIES.entrySet()) {
            if (!entry.getKey().startsWith(prefix)) continue;
            String[] parts = entry.getKey().split("\\|");
            if (parts.length < 3) continue;
            String pointId = parts[1];
            String pollutantCode = parts[2];
            PointDef point = point(pointId);
            PollutantDef pollutant = pollutant(pollutantCode);
            if (point == null || pollutant == null) continue;
            Anomaly anomaly = entry.getValue();
            String ruleCode = switch (anomaly.type()) {
                case "EXCEED" -> "R001";
                case "MISSING" -> "R002";
                case "CONSTANT" -> "R003";
                case "JUMP" -> "R004";
                case "STATUS" -> "R005";
                default -> "R000";
            };
            String severity = "STATUS".equals(anomaly.type()) ? "HIGH"
                    : ("EXCEED".equals(anomaly.type()) && anomaly.value() >= pollutant.limit() * 1.3 ? "HIGH" : "MEDIUM");
            Map<String, Object> label = new LinkedHashMap<>();
            label.put("bizDate", date.toString());
            label.put("enterpriseId", point.enterpriseId());
            label.put("pointId", pointId);
            label.put("pollutantCode", pollutantCode);
            label.put("ruleCode", ruleCode);
            label.put("anomalyType", anomaly.type());
            label.put("startHour", anomaly.startHour());
            label.put("endHour", anomaly.endHour());
            label.put("expectedSeverity", severity);
            label.put("scenario", anomaly.type());
            label.put("description", point.pointName() + " " + pollutant.name() + " " + anomaly.type()
                    + " " + anomaly.startHour() + ":00-" + anomaly.endHour() + ":00");
            labels.add(label);
        }
        return labels;
    }

    public int totalExpectedPerDay() {
        int total = 0;
        for (PointDef p : POINTS) total += p.pollutants().size() * 24;
        return total;
    }

    public static PointDef point(String id) { return POINT_MAP.get(id); }
    public static PollutantDef pollutant(String code) { return POLLUTANT_MAP.get(code); }
    public static EnterpriseDef enterprise(String id) {
        return ENTERPRISES.stream().filter(e -> e.id().equals(id)).findFirst().orElse(null);
    }

    private static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
