package com.fixedsource.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class CollectService {

    private static final Logger log = LoggerFactory.getLogger(CollectService.class);
    private final JdbcTemplate jdbc;
    private final MockSourceService mockSource;
    private final RuleEngineService ruleEngine;
    private final AiService aiService;
    private final EventRiskService eventRiskService;
    private final LabelService labelService;
    private final DataQualityService dataQualityService;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public CollectService(JdbcTemplate jdbc, MockSourceService mockSource, RuleEngineService ruleEngine,
                          AiService aiService, EventRiskService eventRiskService, LabelService labelService,
                          DataQualityService dataQualityService, ObjectMapper objectMapper,
                          @Value("${app.mock-source-base}") String mockSourceBase) {
        this.jdbc = jdbc;
        this.mockSource = mockSource;
        this.ruleEngine = ruleEngine;
        this.aiService = aiService;
        this.eventRiskService = eventRiskService;
        this.labelService = labelService;
        this.dataQualityService = dataQualityService;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder().baseUrl(mockSourceBase).build();
    }

    public List<Map<String, Object>> listBatches(int limit) {
        return jdbc.queryForList("""
                SELECT id, batch_no AS batchNo, FORMATDATETIME(biz_date,'yyyy-MM-dd') AS bizDate, source, status,
                       expected_count AS expectedCount, received_count AS receivedCount, success_count AS successCount,
                       failed_count AS failedCount, FORMATDATETIME(started_at,'yyyy-MM-dd HH:mm:ss') AS startedAt,
                       FORMATDATETIME(finished_at,'yyyy-MM-dd HH:mm:ss') AS finishedAt, message, original_batch_no AS originalBatchNo
                FROM collect_batch ORDER BY id DESC LIMIT ?
                """, limit);
    }

    public Map<String, Object> getBatch(long id) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT id, batch_no AS batchNo, FORMATDATETIME(biz_date,'yyyy-MM-dd') AS bizDate, source, status,
                       expected_count AS expectedCount, received_count AS receivedCount, success_count AS successCount,
                       failed_count AS failedCount, FORMATDATETIME(started_at,'yyyy-MM-dd HH:mm:ss') AS startedAt,
                       FORMATDATETIME(finished_at,'yyyy-MM-dd HH:mm:ss') AS finishedAt, message, original_batch_no AS originalBatchNo
                FROM collect_batch WHERE id=?
                """, id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public Map<String, Object> collect(LocalDate bizDate, String operator) {
        String batchNo = "CB" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + String.format("%03d", ThreadLocalRandom.current().nextInt(1000));
        jdbc.update("""
                INSERT INTO collect_batch(batch_no,biz_date,source,status,expected_count,received_count,success_count,failed_count,started_at,message)
                VALUES (?,?,?,?,?,?,?,?,?,?)
                """, batchNo, Date.valueOf(bizDate), "MOCK-API", "RUNNING", 0, 0, 0, 0,
                Timestamp.valueOf(LocalDateTime.now()), "按业务日期调用模拟数据源接口");
        int expected = 0;
        int received = 0;
        int failed = 0;
        String message;
        try {
            JsonNode payload = restClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/monitoring").queryParam("bizDate", bizDate).build())
                    .retrieve()
                    .body(JsonNode.class);
            if (payload == null) throw new IllegalStateException("模拟源返回空数据");
            expected = payload.path("expectedCount").asInt(mockSource.totalExpectedPerDay());
            JsonNode records = payload.path("records");
            List<Object[]> args = new ArrayList<>();
            for (JsonNode n : records) {
                try {
                    LocalDateTime time = LocalDateTime.parse(n.path("monitorTime").asText());
                    args.add(new Object[]{
                            n.path("enterpriseId").asText(), n.path("pointId").asText(), n.path("pollutantCode").asText(),
                            Timestamp.valueOf(time), n.path("measuredValue").asDouble(), n.path("convertedValue").asDouble(),
                            n.path("flowValue").asDouble(), n.path("dataStatus").asText("NORMAL"), batchNo
                    });
                } catch (Exception ex) {
                    failed++;
                }
            }
            received = args.size();
            insertRecords(args);
            message = "模拟源接口采集完成";
        } catch (Exception ex) {
            log.warn("Mock source HTTP collection failed, fallback to in-process generator: {}", ex.getMessage());
            List<MockSourceService.MonitoringRow> rows = mockSource.generateDay(bizDate);
            expected = mockSource.totalExpectedPerDay();
            List<Object[]> args = new ArrayList<>();
            for (MockSourceService.MonitoringRow row : rows) {
                args.add(new Object[]{row.enterpriseId(), row.pointId(), row.pollutantCode(), Timestamp.valueOf(row.time()),
                        row.value(), row.converted(), row.flow(), row.status(), batchNo});
            }
            received = args.size();
            insertRecords(args);
            message = "模拟源 HTTP 调用失败，已使用同契约内置生成器兜底";
        }
        labelService.syncDate(bizDate);
        dataQualityService.recordForDate(batchNo, bizDate);
        ruleEngine.runForDate(bizDate, batchNo);
        aiService.assessNewClues(bizDate);
        eventRiskService.rebuildForDate(bizDate);
        eventRiskService.rebuildRiskForAll();
        String status = failed == 0 ? "SUCCESS" : "PARTIAL";
        jdbc.update("""
                UPDATE collect_batch SET status=?, expected_count=?, received_count=?, success_count=?, failed_count=?,
                    finished_at=?, message=? WHERE batch_no=?
                """, status, expected, received, received, failed, Timestamp.valueOf(LocalDateTime.now()), message, batchNo);
        jdbc.update("INSERT INTO audit_log(action,target_type,target_id,operator,detail) VALUES (?,?,?,?,?)",
                "COLLECT", "BATCH", batchNo, operator, "采集 " + bizDate + "，接收 " + received + " 条");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("batchNo", batchNo);
        result.put("bizDate", bizDate.toString());
        result.put("status", status);
        result.put("expectedCount", expected);
        result.put("receivedCount", received);
        result.put("message", message);
        result.put("clues", jdbc.queryForObject("SELECT COUNT(*) FROM clue WHERE batch_no=?", Integer.class, batchNo));
        return result;
    }

    private void insertRecords(List<Object[]> args) {
        if (args.isEmpty()) return;
        jdbc.batchUpdate("""
                MERGE INTO monitoring_record(enterprise_id,point_id,pollutant_code,monitor_time,measured_value,
                    converted_value,flow_value,data_status,batch_no)
                KEY(point_id,pollutant_code,monitor_time)
                VALUES (?,?,?,?,?,?,?,?,?)
                """, args);
    }
}
