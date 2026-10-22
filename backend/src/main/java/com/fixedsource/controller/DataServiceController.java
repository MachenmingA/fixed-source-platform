package com.fixedsource.controller;

import com.fixedsource.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 业务平台的基础数据服务出口。
 * 国家数据仓只通过该接口获取企业、排口、标准与监测基础数据；
 * 规则、事件、风险、AI 和任务闭环属于另一个数据处理项目，不在此处共享。
 */
@RestController
@RequestMapping("/api/data-service/v1")
public class DataServiceController {

    private final JdbcTemplate jdbc;
    private final AuthService authService;

    public DataServiceController(JdbcTemplate jdbc, AuthService authService) {
        this.jdbc = jdbc;
        this.authService = authService;
    }

    @GetMapping("/export/{datasetCode}")
    public Map<String, Object> export(@PathVariable String datasetCode,
                                      @RequestParam(required = false) String bizDate,
                                      HttpServletRequest request) {
        AuthService.User user = (AuthService.User) request.getAttribute("user");
        if (!authService.hasAnyRole(user, "reviewer", "admin", "system")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前角色无权调用基础数据服务出口");
        }
        String code = datasetCode == null ? "" : datasetCode.trim().toUpperCase();
        LocalDate date = parseDate(bizDate);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("datasetCode", code);
        result.put("datasetName", datasetName(code));
        result.put("dataOwner", "净源智研固定污染源基础数据平台");
        result.put("generatedAt", LocalDateTime.now().toString());
        result.put("bizDate", date == null ? null : date.toString());
        List<Map<String, Object>> records = switch (code) {
            case "ENTERPRISE_MASTER" -> enterpriseRecords();
            case "MONITOR_POINT_MASTER" -> pointRecords();
            case "POLLUTANT_STANDARD" -> pollutantRecords();
            case "MONITORING_HOURLY" -> monitoringRecords(date == null ? LocalDate.now() : date);
            case "MONITORING_DAILY" -> dailyMonitoringRecords(date == null ? LocalDate.now() : date);
            case "KEY_DISCHARGE_UNIT" -> referenceRecords("KEY_DISCHARGE_UNIT");
            case "STANDARD_LIBRARY" -> referenceRecords("STANDARD_LIBRARY");
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "该数据目录不属于基础数据域：" + datasetCode);
        };
        result.put("recordCount", records.size());
        result.put("records", records);
        jdbc.update("INSERT INTO audit_log(action,target_type,target_id,operator,detail) VALUES (?,?,?,?,?)",
                "EXPORT_BASE_DATA", "DATASET", code, user == null ? "system" : user.name(),
                "基础数据服务出口导出 " + records.size() + " 条");
        return result;
    }

    private List<Map<String, Object>> enterpriseRecords() {
        List<Map<String, Object>> enterprises = jdbc.queryForList("""
                SELECT id, name, industry, region, management_level AS management_level,
                       credit_code AS credit_code, address, legal_representative AS legal_representative,
                       contact_name AS contact_name, contact_phone AS contact_phone,
                       longitude, latitude, production_status AS production_status, permit_no AS permit_no,
                       status, remark, FORMATDATETIME(updated_at,'yyyy-MM-dd HH:mm') AS updated_at
                FROM enterprise ORDER BY id
                """);
        for (Map<String, Object> enterprise : enterprises) {
            List<Map<String, Object>> points = jdbc.queryForList("""
                    SELECT id, outlet_name AS outlet_name, point_name AS point_name,
                           point_type AS point_type, status
                    FROM monitor_point WHERE enterprise_id=? ORDER BY id
                    """, enterprise.get("id"));
            enterprise.put("points", points);
        }
        return enterprises;
    }

    private List<Map<String, Object>> pointRecords() {
        List<Map<String, Object>> points = jdbc.queryForList("""
                SELECT p.id, p.enterprise_id AS enterprise_id, e.name AS enterprise_name, e.region,
                       p.outlet_name AS outlet_name, p.point_name AS point_name,
                       p.point_type AS point_type, p.status,
                       FORMATDATETIME(p.updated_at,'yyyy-MM-dd HH:mm') AS updated_at
                FROM monitor_point p JOIN enterprise e ON e.id=p.enterprise_id
                ORDER BY p.id
                """);
        for (Map<String, Object> point : points) {
            List<Map<String, Object>> pollutants = jdbc.queryForList("""
                    SELECT DISTINCT po.code, po.name, po.unit, po.limit_value AS limit_value,
                           po.standard_ref AS standard_ref
                    FROM monitoring_record mr JOIN pollutant po ON po.code=mr.pollutant_code
                    WHERE mr.point_id=? ORDER BY po.code
                    """, point.get("id"));
            point.put("pollutants", pollutants);
        }
        return points;
    }

    private List<Map<String, Object>> pollutantRecords() {
        return jdbc.queryForList("""
                SELECT code, name, unit, limit_value AS limit_value, standard_ref AS standard_ref
                FROM pollutant ORDER BY code
                """);
    }

    private List<Map<String, Object>> monitoringRecords(LocalDate date) {
        return jdbc.queryForList("""
                SELECT mr.enterprise_id AS enterprise_id, e.name AS enterprise_name, e.region,
                       mr.point_id AS point_id, p.point_name AS point_name,
                       mr.pollutant_code AS pollutant_code, po.name AS pollutant_name, po.unit AS unit,
                       FORMATDATETIME(mr.monitor_time,'yyyy-MM-dd HH:mm:ss') AS monitor_time,
                       mr.measured_value AS measured_value, mr.converted_value AS converted_value,
                       mr.flow_value AS flow_value, mr.data_status AS data_status, mr.batch_no AS batch_no
                FROM monitoring_record mr
                JOIN enterprise e ON e.id=mr.enterprise_id
                JOIN monitor_point p ON p.id=mr.point_id
                JOIN pollutant po ON po.code=mr.pollutant_code
                WHERE mr.monitor_time >= ? AND mr.monitor_time < ?
                ORDER BY mr.monitor_time, mr.point_id, mr.pollutant_code
                """, Timestamp.valueOf(date.atStartOfDay()), Timestamp.valueOf(date.plusDays(1).atStartOfDay()));
    }

    private List<Map<String, Object>> dailyMonitoringRecords(LocalDate date) {
        return jdbc.queryForList("""
                SELECT mr.enterprise_id AS enterprise_id, e.name AS enterprise_name, e.region,
                       mr.point_id AS point_id, p.point_name AS point_name,
                       mr.pollutant_code AS pollutant_code, po.name AS pollutant_name, po.unit AS unit,
                       po.limit_value AS limit_value, po.standard_ref AS standard_ref,
                       FORMATDATETIME(CAST(mr.monitor_time AS DATE),'yyyy-MM-dd') AS biz_date,
                       COUNT(*) AS sample_count,
                       ROUND(MIN(mr.measured_value),4) AS min_value,
                       ROUND(MAX(mr.measured_value),4) AS max_value,
                       ROUND(AVG(mr.measured_value),4) AS avg_value,
                       SUM(CASE WHEN mr.data_status <> 'NORMAL' THEN 1 ELSE 0 END) AS abnormal_count
                FROM monitoring_record mr
                JOIN enterprise e ON e.id=mr.enterprise_id
                JOIN monitor_point p ON p.id=mr.point_id
                JOIN pollutant po ON po.code=mr.pollutant_code
                WHERE mr.monitor_time >= ? AND mr.monitor_time < ?
                GROUP BY mr.enterprise_id, e.name, e.region, mr.point_id, p.point_name,
                         mr.pollutant_code, po.name, po.unit, po.limit_value, po.standard_ref,
                         FORMATDATETIME(CAST(mr.monitor_time AS DATE),'yyyy-MM-dd')
                ORDER BY mr.enterprise_id, mr.point_id, mr.pollutant_code
                """, Timestamp.valueOf(date.atStartOfDay()), Timestamp.valueOf(date.plusDays(1).atStartOfDay()));
    }

    private List<Map<String, Object>> referenceRecords(String datasetCode) {
        return jdbc.queryForList("""
                SELECT record_key AS record_key, title, category, region, credit_code AS credit_code,
                       standard_code AS standard_code, issuer, scope_text AS scope_text,
                       source_name AS source_name, source_url AS source_url, annual,
                       FORMATDATETIME(publish_date,'yyyy-MM-dd') AS publish_date,
                       FORMATDATETIME(effective_date,'yyyy-MM-dd') AS effective_date
                FROM public_reference_item WHERE dataset_code=? ORDER BY record_key
                """, datasetCode);
    }

    private LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return LocalDate.parse(value);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "bizDate 格式应为 yyyy-MM-dd");
        }
    }

    private String datasetName(String code) {
        return switch (code) {
            case "ENTERPRISE_MASTER" -> "企业基础信息";
            case "MONITOR_POINT_MASTER" -> "排口与监测点";
            case "POLLUTANT_STANDARD" -> "污染物与排放限值";
            case "MONITORING_HOURLY" -> "小时监测明细";
            case "MONITORING_DAILY" -> "日监测指标";
            case "KEY_DISCHARGE_UNIT" -> "重点排污单位公开名录";
            case "STANDARD_LIBRARY" -> "公开标准与依据";
            default -> code;
        };
    }
}
