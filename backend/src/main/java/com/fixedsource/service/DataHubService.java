package com.fixedsource.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class DataHubService {

    private static final Logger log = LoggerFactory.getLogger(DataHubService.class);
    private static final DateTimeFormatter MINUTE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final DataHubSourceClient sourceClient;
    private final RestClient restClient;
    private final String receiverBase;
    private final String openKey;

    public DataHubService(JdbcTemplate jdbc, ObjectMapper objectMapper, DataHubSourceClient sourceClient,
                          @Value("${app.data-hub.receiver-base:http://127.0.0.1:8080}") String receiverBase,
                          @Value("${app.data-hub.open-key:national-open-demo-key}") String openKey) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.sourceClient = sourceClient;
        this.restClient = RestClient.create();
        this.receiverBase = receiverBase;
        this.openKey = openKey;
    }

    public Map<String, Object> overview() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("catalogCount", n("SELECT COUNT(*) FROM data_catalog"));
        result.put("subscriberCount", n("SELECT COUNT(*) FROM data_subscriber"));
        result.put("enabledSubscriberCount", n("SELECT COUNT(*) FROM data_subscriber WHERE enabled=TRUE"));
        result.put("packageCount", n("SELECT COUNT(*) FROM data_package"));
        result.put("dispatchCount", n("SELECT COUNT(*) FROM data_dispatch"));
        int success = n("SELECT COUNT(*) FROM data_dispatch WHERE status='SENT'");
        int failed = n("SELECT COUNT(*) FROM data_dispatch WHERE status='FAILED'");
        result.put("successCount", success);
        result.put("failedCount", failed);
        result.put("successRate", success + failed == 0 ? 0
                : Math.round(success * 1000.0 / (success + failed)) / 10.0);
        result.put("totalRecords", n("SELECT COALESCE(SUM(record_count),0) FROM data_package"));
        result.put("todayDispatch", n("SELECT COUNT(*) FROM data_dispatch WHERE sent_at >= ?",
                Timestamp.valueOf(LocalDate.now().atStartOfDay())));
        result.put("pendingRetry", failed);
        result.put("openDataKey", openKey);
        result.put("openDataBase", "/open-data/v1");
        List<Map<String, Object>> latest = jdbc.queryForList("""
                SELECT FORMATDATETIME(MAX(created_at),'yyyy-MM-dd HH:mm') AS latest_published_at
                FROM data_package
                """);
        result.put("latestPublishedAt", latest.isEmpty() ? null : latest.get(0).get("latest_published_at"));
        result.put("catalogs", catalogRows());
        result.put("subscribers", subscriberRows());
        result.put("packages", packageRows(12));
        result.put("dispatches", dispatchRows(14, null));
        result.put("statusStats", jdbc.queryForList("""
                SELECT status, COUNT(*) AS count FROM data_dispatch GROUP BY status ORDER BY status
                """));
        result.put("dispatchTrend", dispatchTrend());
        result.put("latestPackage", packageRows(1).stream().findFirst().orElse(null));
        return result;
    }

    public Map<String, Object> publish(String datasetCode, String bizDate, String operator) {
        String code = datasetCode == null ? "" : datasetCode.trim().toUpperCase();
        Map<String, Object> catalog = catalogRow(code);
        if (catalog == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "数据目录不存在");
        if (!Boolean.TRUE.equals(catalog.get("enabled"))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "数据目录已停用");
        }
        JsonNode export;
        try {
            export = sourceClient.export(code, bizDate, false);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "业务平台数据接口调用失败：" + e.getMessage());
        }
        if (export == null) throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "业务平台返回空数据");
        int recordCount = export.path("recordCount").asInt(0);
        String packageNo = "DP" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + String.format("%03d", ThreadLocalRandom.current().nextInt(1000));
        String generatedAt = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String bodyJson;
        String payloadHash;
        try {
            bodyJson = objectMapper.writeValueAsString(export);
            payloadHash = sha256(bodyJson);
            Map<String, Object> header = new LinkedHashMap<>();
            header.put("packageNo", packageNo);
            header.put("datasetCode", code);
            header.put("datasetName", catalog.get("name"));
            header.put("dataOwner", "净源智研固定污染源企业画像与研判平台");
            header.put("generatedAt", generatedAt);
            header.put("recordCount", recordCount);
            header.put("sensitivity", catalog.get("sensitivity"));
            header.put("schemaVersion", "1.0");
            header.put("hash", payloadHash);
            Map<String, Object> envelope = new LinkedHashMap<>();
            envelope.put("header", header);
            envelope.put("body", export);
            String payloadJson = objectMapper.writeValueAsString(envelope);
            String resolvedBizDate = export.path("bizDate").isMissingNode() || export.path("bizDate").isNull()
                    ? null : export.path("bizDate").asText();
            jdbc.update("""
                    INSERT INTO data_package(package_no,dataset_code,dataset_name,biz_date,record_count,payload_json,
                        payload_hash,status,created_by)
                    VALUES (?,?,?,?,?,?,?,'PUBLISHED',?)
                    """, packageNo, code, catalog.get("name"),
                    resolvedBizDate == null || resolvedBizDate.isBlank() ? null : Date.valueOf(resolvedBizDate),
                    recordCount, payloadJson, payloadHash, operator);
            Long packageId = jdbc.queryForObject("SELECT id FROM data_package WHERE package_no=?", Long.class, packageNo);
            List<Map<String, Object>> targets = dispatchSubscribers(code);
            for (Map<String, Object> target : targets) {
                try {
                    sendDispatch(packageId, packageNo, code, payloadHash, payloadJson, target, null);
                } catch (Exception e) {
                    log.warn("Data package {} dispatch to {} failed: {}", packageNo, target.get("code"), e.getMessage());
                }
            }
            refreshPackageStatus(packageId);
            audit(operator, "PUBLISH_DATA", packageNo, "发布数据包 " + code + "，记录 " + recordCount
                    + " 条，订阅方 " + targets.size() + " 个");
            return packageDetail(packageId);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "数据包发布失败：" + e.getMessage(), e);
        }
    }

    public Map<String, Object> publishMany(List<String> datasetCodes, String bizDate, String operator) {
        List<Map<String, Object>> results = new ArrayList<>();
        for (String code : datasetCodes) {
            try {
                results.add(publish(code, bizDate, operator));
            } catch (Exception e) {
                Map<String, Object> failure = new LinkedHashMap<>();
                failure.put("datasetCode", code);
                failure.put("success", false);
                failure.put("message", e.getMessage());
                results.add(failure);
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("published", results.stream().filter(x -> x.get("packageNo") != null).count());
        result.put("failed", results.stream().filter(x -> x.get("success") == Boolean.FALSE).count());
        result.put("packages", results);
        return result;
    }

    public List<Map<String, Object>> packageRows(int limit) {
        return jdbc.queryForList("""
                SELECT p.id, p.package_no AS package_no, p.dataset_code AS dataset_code,
                       p.dataset_name AS dataset_name, FORMATDATETIME(p.biz_date,'yyyy-MM-dd') AS biz_date,
                       p.record_count AS record_count, p.status, p.created_by AS created_by,
                       FORMATDATETIME(p.created_at,'yyyy-MM-dd HH:mm') AS created_at,
                       (SELECT COUNT(*) FROM data_dispatch d WHERE d.package_id=p.id) AS dispatch_count,
                       (SELECT COUNT(*) FROM data_dispatch d WHERE d.package_id=p.id AND d.status='SENT') AS success_count,
                       (SELECT COUNT(*) FROM data_dispatch d WHERE d.package_id=p.id AND d.status='FAILED') AS failed_count
                FROM data_package p ORDER BY p.id DESC LIMIT ?
                """, Math.min(100, Math.max(1, limit)));
    }

    public List<Map<String, Object>> dispatchRows(int limit, String status) {
        StringBuilder sql = new StringBuilder("""
                SELECT d.id, d.dispatch_no AS dispatch_no, d.package_id AS package_id,
                       d.package_no AS package_no, d.subscriber_code AS subscriber_code,
                       d.subscriber_name AS subscriber_name, d.dataset_code AS dataset_code,
                       d.status, d.http_status AS http_status, d.latency_ms AS latency_ms,
                       d.retry_count AS retry_count, d.error_message AS error_message,
                       FORMATDATETIME(d.sent_at,'yyyy-MM-dd HH:mm:ss') AS sent_at
                FROM data_dispatch d WHERE 1=1
                """);
        List<Object> args = new ArrayList<>();
        if (status != null && !status.isBlank()) {
            sql.append(" AND d.status=?");
            args.add(status);
        }
        sql.append(" ORDER BY d.id DESC LIMIT ?");
        args.add(Math.min(200, Math.max(1, limit)));
        return jdbc.queryForList(sql.toString(), args.toArray());
    }

    public Map<String, Object> packageDetail(long id) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT p.id, p.package_no AS package_no, p.dataset_code AS dataset_code,
                       p.dataset_name AS dataset_name, FORMATDATETIME(p.biz_date,'yyyy-MM-dd') AS biz_date,
                       p.record_count AS record_count, p.payload_json AS payload_json,
                       p.payload_hash AS payload_hash, p.status, p.created_by AS created_by,
                       FORMATDATETIME(p.created_at,'yyyy-MM-dd HH:mm') AS created_at
                FROM data_package p WHERE p.id=?
                """, id);
        if (rows.isEmpty()) return null;
        Map<String, Object> row = new LinkedHashMap<>(rows.get(0));
        String payloadJson = extractPayload(row);
        row.remove("payload_json");
        row.remove("PAYLOAD_JSON");
        row.remove("payloadJson");
        row.put("payloadPreview", payloadJson.length() <= 6000 ? payloadJson : payloadJson.substring(0, 6000) + "\n…");
        row.put("payloadSize", payloadJson.getBytes(StandardCharsets.UTF_8).length);
        row.put("dispatches", jdbc.queryForList("""
                SELECT d.id, d.dispatch_no AS dispatch_no, d.subscriber_code AS subscriber_code,
                       d.subscriber_name AS subscriber_name, d.dataset_code AS dataset_code, d.status,
                       d.http_status AS http_status, d.latency_ms AS latency_ms, d.retry_count AS retry_count,
                       d.error_message AS error_message,
                       FORMATDATETIME(d.sent_at,'yyyy-MM-dd HH:mm:ss') AS sent_at,
                       (SELECT r.receipt_no FROM data_receipt r WHERE r.package_no=d.package_no
                         AND r.subscriber_code=d.subscriber_code LIMIT 1) AS receipt_no
                FROM data_dispatch d WHERE d.package_id=? ORDER BY d.id
                """, id));
        return row;
    }

    public byte[] packagePayload(long id) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT payload_json AS payload_json FROM data_package WHERE id=?", id);
        if (rows.isEmpty()) return null;
        return extractPayload(rows.get(0)).getBytes(StandardCharsets.UTF_8);
    }

    private String extractPayload(Map<String, Object> row) {
        Object payload = row.get("payload_json");
        if (payload == null) payload = row.get("PAYLOAD_JSON");
        if (payload == null) payload = row.get("payloadJson");
        if (payload == null) return "{}";
        if (payload instanceof java.sql.Clob clob) {
            try {
                return clob.getSubString(1, (int) clob.length());
            } catch (Exception ignored) {
                return "{}";
            }
        }
        return String.valueOf(payload);
    }

    public Map<String, Object> retry(long dispatchId, String operator) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT d.*, p.payload_json AS payload_json, p.payload_hash AS payload_hash, p.dataset_code AS dataset_code
                FROM data_dispatch d JOIN data_package p ON p.id=d.package_id WHERE d.id=?
                """, dispatchId);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "投递记录不存在");
        Map<String, Object> row = rows.get(0);
        if ("SENT".equals(String.valueOf(row.get("status")))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "该投递已成功，无需重试");
        }
        Map<String, Object> subscriber = subscriberRow(((Number) row.get("subscriber_id")).longValue());
        if (subscriber == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "订阅系统不存在");
        String payloadJson = String.valueOf(row.get("payload_json"));
        try {
            sendDispatch(((Number) row.get("package_id")).longValue(), String.valueOf(row.get("package_no")),
                    String.valueOf(row.get("dataset_code")), String.valueOf(row.get("payload_hash")),
                    payloadJson, subscriber, dispatchId);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "重试失败：" + e.getMessage());
        }
        refreshPackageStatus(((Number) row.get("package_id")).longValue());
        audit(operator, "RETRY_DISPATCH", String.valueOf(dispatchId), "人工重试数据投递");
        return dispatchRow(dispatchId);
    }

    public List<Map<String, Object>> subscriberRows() {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT s.id, s.code, s.name, s.system_type AS system_type, s.endpoint,
                       s.app_key AS app_key, s.datasets, s.enabled,
                       FORMATDATETIME(s.last_dispatch_at,'yyyy-MM-dd HH:mm') AS last_dispatch_at,
                       (SELECT COUNT(*) FROM data_dispatch d WHERE d.subscriber_code=s.code) AS dispatch_count,
                       (SELECT COUNT(*) FROM data_dispatch d WHERE d.subscriber_code=s.code AND d.status='SENT') AS success_count,
                       (SELECT COUNT(*) FROM data_dispatch d WHERE d.subscriber_code=s.code AND d.status='FAILED') AS failed_count,
                       (SELECT FORMATDATETIME(MAX(r.received_at),'yyyy-MM-dd HH:mm') FROM data_receipt r
                         WHERE r.subscriber_code=s.code) AS last_receipt_at
                FROM data_subscriber s ORDER BY s.id
                """);
        for (Map<String, Object> row : rows) {
            row.put("datasetList", splitDatasets(String.valueOf(row.get("datasets"))));
            row.put("appSecretMasked", "••••••••");
        }
        return rows;
    }

    public Map<String, Object> createSubscriber(Map<String, Object> body, String operator) {
        String code = requireText(body.get("code"), "订阅方编码").toUpperCase();
        String name = requireText(body.get("name"), "订阅方名称");
        String endpoint = requireText(body.get("endpoint"), "接收地址");
        String appKey = requireText(body.get("appKey"), "AppKey");
        String appSecret = requireText(body.get("appSecret"), "AppSecret");
        String datasets = normalizeDatasets(body.get("datasets"));
        boolean enabled = body.get("enabled") == null || Boolean.parseBoolean(String.valueOf(body.get("enabled")));
        Integer exists = jdbc.queryForObject("SELECT COUNT(*) FROM data_subscriber WHERE code=? OR app_key=?",
                Integer.class, code, appKey);
        if (exists != null && exists > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "订阅方编码或 AppKey 已存在");
        }
        jdbc.update("""
                INSERT INTO data_subscriber(code,name,system_type,endpoint,app_key,app_secret,datasets,enabled)
                VALUES (?,?,?,?,?,?,?,?)
                """, code, name, text(body.get("systemType"), "市级"), endpoint, appKey, appSecret, datasets, enabled);
        audit(operator, "CREATE_SUBSCRIBER", code, "新增数据订阅方 " + name);
        return subscriberRows().stream().filter(row -> code.equals(row.get("code"))).findFirst().orElse(Map.of());
    }

    public Map<String, Object> updateSubscriber(long id, Map<String, Object> body, String operator) {
        Map<String, Object> current = subscriberRow(id);
        if (current == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "订阅系统不存在");
        String name = valueOr(body.get("name"), String.valueOf(current.get("name")));
        String systemType = valueOr(body.get("systemType"), String.valueOf(current.get("system_type")));
        String endpoint = valueOr(body.get("endpoint"), String.valueOf(current.get("endpoint")));
        String appKey = valueOr(body.get("appKey"), String.valueOf(current.get("app_key")));
        String datasets = body.containsKey("datasets") ? normalizeDatasets(body.get("datasets"))
                : String.valueOf(current.get("datasets"));
        boolean enabled = body.get("enabled") == null
                ? Boolean.TRUE.equals(current.get("enabled"))
                : Boolean.parseBoolean(String.valueOf(body.get("enabled")));
        String secret = text(body.get("appSecret"), "");
        Integer duplicate = jdbc.queryForObject("SELECT COUNT(*) FROM data_subscriber WHERE app_key=? AND id<>?",
                Integer.class, appKey, id);
        if (duplicate != null && duplicate > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "AppKey 已被其他订阅方使用");
        }
        if (secret.isBlank()) {
            jdbc.update("""
                    UPDATE data_subscriber SET name=?, system_type=?, endpoint=?, app_key=?, datasets=?, enabled=?,
                        updated_at=CURRENT_TIMESTAMP WHERE id=?
                    """, name, systemType, endpoint, appKey, datasets, enabled, id);
        } else {
            jdbc.update("""
                    UPDATE data_subscriber SET name=?, system_type=?, endpoint=?, app_key=?, app_secret=?, datasets=?,
                        enabled=?, updated_at=CURRENT_TIMESTAMP WHERE id=?
                    """, name, systemType, endpoint, appKey, secret, datasets, enabled, id);
        }
        audit(operator, "UPDATE_SUBSCRIBER", String.valueOf(id), "维护数据订阅方 " + name);
        return subscriberRows().stream().filter(row -> ((Number) row.get("id")).longValue() == id)
                .findFirst().orElse(Map.of());
    }

    public Map<String, Object> openCatalog() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("repository", "国家生态环境公共数据信息库");
        result.put("owner", "生态环境数据共享服务节点");
        result.put("updatedAt", LocalDateTime.now().format(MINUTE));
        result.put("catalogs", catalogRows());
        return result;
    }

    public Map<String, Object> openLatest(String datasetCode) {
        String code = datasetCode == null ? "" : datasetCode.trim().toUpperCase();
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT package_no AS package_no, dataset_code AS dataset_code, dataset_name AS dataset_name,
                       FORMATDATETIME(biz_date,'yyyy-MM-dd') AS biz_date, record_count AS record_count,
                       payload_json AS payload_json, payload_hash AS payload_hash,
                       FORMATDATETIME(created_at,'yyyy-MM-dd HH:mm:ss') AS published_at
                FROM data_package WHERE dataset_code=? ORDER BY id DESC LIMIT 1
                """, code);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "该目录暂无已发布数据包");
        Map<String, Object> row = rows.get(0);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("retrievedAt", LocalDateTime.now().toString());
        result.put("packageNo", row.get("package_no"));
        result.put("datasetCode", row.get("dataset_code"));
        result.put("datasetName", row.get("dataset_name"));
        result.put("bizDate", row.get("biz_date"));
        result.put("recordCount", row.get("record_count"));
        result.put("hash", row.get("payload_hash"));
        result.put("publishedAt", row.get("published_at"));
        try {
            result.put("payload", objectMapper.readValue(extractPayload(row),
                    new TypeReference<Map<String, Object>>() {}));
        } catch (Exception ignored) {
            result.put("payload", Map.of());
        }
        return result;
    }

    public Map<String, Object> receive(String system, String rawBody, String appKey,
                                       String timestamp, String signature) {
        if (appKey == null || appKey.isBlank() || signature == null || signature.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "缺少 AppKey 或签名");
        }
        String code = system == null ? "" : system.toUpperCase();
        Map<String, Object> subscriber = jdbc.queryForList("""
                SELECT id, code, name, app_secret AS app_secret FROM data_subscriber WHERE app_key=?
                """, appKey).stream().findFirst().orElse(null);
        if (subscriber == null || !code.equalsIgnoreCase(String.valueOf(subscriber.get("code")))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "订阅方身份不匹配");
        }
        try {
            long ts = Long.parseLong(timestamp);
            if (Math.abs(System.currentTimeMillis() - ts) > 10 * 60 * 1000L) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请求时间戳已过期");
            }
            JsonNode root = objectMapper.readTree(rawBody);
            String packageNo = root.path("header").path("packageNo").asText();
            if (packageNo.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "缺少 packageNo");
            String expected = hmacSha256(String.valueOf(subscriber.get("app_secret")),
                    timestamp + "\n" + packageNo + "\n" + sha256(rawBody));
            if (!expected.equalsIgnoreCase(signature)) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "HMAC 签名校验失败");
            }
            List<Map<String, Object>> existing = jdbc.queryForList("""
                    SELECT receipt_no AS receipt_no FROM data_receipt WHERE package_no=? AND subscriber_code=?
                    """, packageNo, code);
            if (!existing.isEmpty()) {
                return receiptResponse(true, String.valueOf(existing.get(0).get("receipt_no")),
                        "重复投递已幂等接收", packageNo, code);
            }
            int recordCount = root.path("header").path("recordCount").asInt(0);
            String datasetCode = root.path("header").path("datasetCode").asText();
            String receiptNo = "RC" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                    + String.format("%03d", ThreadLocalRandom.current().nextInt(1000));
            jdbc.update("""
                    INSERT INTO data_receipt(receipt_no,package_no,subscriber_code,dataset_code,record_count,
                        payload_hash,app_key,signature,status,summary)
                    VALUES (?,?,?,?,?,?,?,?,?,?)
                    """, receiptNo, packageNo, code, datasetCode, recordCount,
                    sha256(rawBody), appKey, signature, "ACCEPTED", "HMAC-SHA256 验签通过，数据包已入库");
            return receiptResponse(true, receiptNo, "数据包接收成功", packageNo, code);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "回执处理失败：" + e.getMessage());
        }
    }

    private Map<String, Object> receiptResponse(boolean accepted, String receiptNo, String message,
                                                String packageNo, String code) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("accepted", accepted);
        result.put("receiptNo", receiptNo);
        result.put("message", message);
        result.put("packageNo", packageNo);
        result.put("subscriberCode", code);
        result.put("receivedAt", LocalDateTime.now().toString());
        result.put("validation", "HMAC-SHA256");
        return result;
    }

    private void sendDispatch(long packageId, String packageNo, String datasetCode, String payloadHash,
                              String payloadJson, Map<String, Object> subscriber, Long existingDispatchId) {
        String timestamp = String.valueOf(System.currentTimeMillis());
        String secret = String.valueOf(subscriber.get("app_secret") == null ? "" : subscriber.get("app_secret"));
        String signature = hmacSha256(secret, timestamp + "\n" + packageNo + "\n" + sha256(payloadJson));
        String endpoint = resolveEndpoint(String.valueOf(subscriber.get("endpoint")));
        long started = System.currentTimeMillis();
        int httpStatus = 0;
        String responseBody = "";
        String error = null;
        boolean success = false;
        try {
            ResponseEntity<String> response = restClient.post()
                    .uri(endpoint)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-DataHub-App-Key", String.valueOf(subscriber.get("app_key")))
                    .header("X-DataHub-Timestamp", timestamp)
                    .header("X-DataHub-Signature", signature)
                    .header("X-DataHub-Package-No", packageNo)
                    .body(payloadJson)
                    .retrieve()
                    .toEntity(String.class);
            httpStatus = response.getStatusCode().value();
            responseBody = response.getBody() == null ? "" : response.getBody();
            success = response.getStatusCode().is2xxSuccessful();
        } catch (RestClientResponseException e) {
            httpStatus = e.getStatusCode().value();
            responseBody = e.getResponseBodyAsString();
            error = "接收方返回 HTTP " + httpStatus;
        } catch (Exception e) {
            error = e.getMessage() == null ? "接收方不可达" : e.getMessage();
        }
        int latency = (int) Math.min(Integer.MAX_VALUE, System.currentTimeMillis() - started);
        String status = success ? "SENT" : "FAILED";
        String safeResponse = truncate(responseBody, 1000);
        String safeError = truncate(error, 500);
        if (existingDispatchId != null) {
            jdbc.update("""
                    UPDATE data_dispatch SET status=?, http_status=?, latency_ms=?, retry_count=retry_count+1,
                        signature=?, response_body=?, error_message=?, sent_at=CURRENT_TIMESTAMP
                    WHERE id=?
                    """, status, httpStatus, latency, signature, safeResponse, safeError, existingDispatchId);
        } else {
            String dispatchNo = "DS" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                    + String.format("%03d", ThreadLocalRandom.current().nextInt(1000));
            jdbc.update("""
                    INSERT INTO data_dispatch(dispatch_no,package_id,package_no,subscriber_id,subscriber_code,
                        subscriber_name,dataset_code,endpoint,status,http_status,latency_ms,retry_count,payload_hash,
                        signature,response_body,error_message,sent_at)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP)
                    """, dispatchNo, packageId, packageNo, ((Number) subscriber.get("id")).longValue(),
                    subscriber.get("code"), subscriber.get("name"), datasetCode, endpoint, status, httpStatus, latency,
                    0, payloadHash, signature, safeResponse, safeError);
        }
        jdbc.update("UPDATE data_subscriber SET last_dispatch_at=CURRENT_TIMESTAMP WHERE id=?",
                ((Number) subscriber.get("id")).longValue());
        if (!success) {
            log.warn("Dispatch {} to {} failed: {} {}", packageNo, subscriber.get("code"), httpStatus, safeError);
        }
    }

    private void refreshPackageStatus(long packageId) {
        int total = n("SELECT COUNT(*) FROM data_dispatch WHERE package_id=?", packageId);
        int success = n("SELECT COUNT(*) FROM data_dispatch WHERE package_id=? AND status='SENT'", packageId);
        int failed = n("SELECT COUNT(*) FROM data_dispatch WHERE package_id=? AND status='FAILED'", packageId);
        String status = total == 0 ? "PUBLISHED" : success == total ? "DISTRIBUTED"
                : success == 0 ? "FAILED" : "PARTIAL";
        jdbc.update("UPDATE data_package SET status=? WHERE id=?", status, packageId);
    }

    private List<Map<String, Object>> catalogRows() {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT c.id, c.code, c.name, c.category, c.sensitivity, c.source_api AS source_api,
                       c.description, c.enabled,
                       (SELECT COUNT(*) FROM data_package p WHERE p.dataset_code=c.code) AS package_count,
                       (SELECT FORMATDATETIME(MAX(p.created_at),'yyyy-MM-dd HH:mm') FROM data_package p
                         WHERE p.dataset_code=c.code) AS last_published_at,
                       (SELECT MAX(p.record_count) FROM data_package p WHERE p.dataset_code=c.code) AS last_record_count,
                       (SELECT COUNT(*) FROM data_subscriber s WHERE s.enabled=TRUE
                         AND s.datasets LIKE CONCAT('%',c.code,'%')) AS subscriber_count
                FROM data_catalog c ORDER BY c.id
                """);
        return rows;
    }

    private Map<String, Object> catalogRow(String code) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT id, code, name, category, sensitivity, source_api AS source_api, description, enabled
                FROM data_catalog WHERE code=?
                """, code);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private List<Map<String, Object>> dispatchSubscribers(String code) {
        return jdbc.queryForList("""
                SELECT id, code, name, app_key, app_secret, endpoint, datasets
                FROM data_subscriber WHERE enabled=TRUE ORDER BY id
                """).stream()
                .filter(row -> splitDatasets(String.valueOf(row.get("datasets"))).contains(code))
                .toList();
    }

    private Map<String, Object> dispatchRow(long id) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT id, dispatch_no AS dispatch_no, package_no AS package_no, subscriber_code AS subscriber_code,
                       subscriber_name AS subscriber_name, dataset_code AS dataset_code, status,
                       http_status AS http_status, latency_ms AS latency_ms, retry_count AS retry_count,
                       error_message AS error_message, FORMATDATETIME(sent_at,'yyyy-MM-dd HH:mm:ss') AS sent_at
                FROM data_dispatch WHERE id=?
                """, id);
        return rows.isEmpty() ? Map.of() : rows.get(0);
    }

    private Map<String, Object> subscriberRow(long id) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT id, code, name, system_type, endpoint, app_key, app_secret, datasets, enabled
                FROM data_subscriber WHERE id=?
                """, id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private List<Map<String, Object>> dispatchTrend() {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT FORMATDATETIME(sent_at,'yyyy-MM-dd') AS biz_day, COUNT(*) AS total,
                       SUM(CASE WHEN status='SENT' THEN 1 ELSE 0 END) AS success,
                       SUM(CASE WHEN status='FAILED' THEN 1 ELSE 0 END) AS failed
                FROM data_dispatch WHERE sent_at >= ?
                GROUP BY FORMATDATETIME(sent_at,'yyyy-MM-dd') ORDER BY biz_day
                """, Timestamp.valueOf(LocalDate.now().minusDays(6).atStartOfDay()));
        Map<String, Map<String, Object>> byDay = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) byDay.put(String.valueOf(row.get("biz_day")), row);
        List<Map<String, Object>> trend = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            String day = LocalDate.now().minusDays(i).toString();
            Map<String, Object> row = byDay.get(day);
            if (row == null) {
                row = new LinkedHashMap<>();
                row.put("biz_day", day);
                row.put("total", 0);
                row.put("success", 0);
                row.put("failed", 0);
            }
            trend.add(row);
        }
        return trend;
    }

    private String resolveEndpoint(String endpoint) {
        if (endpoint == null) return receiverBase;
        if (endpoint.startsWith("http://") || endpoint.startsWith("https://")) return endpoint;
        return receiverBase + (endpoint.startsWith("/") ? endpoint : "/" + endpoint);
    }

    private List<String> splitDatasets(String value) {
        if (value == null || value.isBlank()) return List.of();
        List<String> result = new ArrayList<>();
        for (String item : value.split(",")) {
            if (!item.isBlank()) result.add(item.trim().toUpperCase());
        }
        return result;
    }

    private String normalizeDatasets(Object value) {
        if (value == null) return "";
        if (value instanceof List<?> list) {
            List<String> items = new ArrayList<>();
            for (Object item : list) if (item != null && !String.valueOf(item).isBlank()) items.add(String.valueOf(item).trim().toUpperCase());
            return String.join(",", items);
        }
        return String.valueOf(value).replace(" ", "").toUpperCase();
    }

    private String requireText(Object value, String label) {
        if (value == null || String.valueOf(value).isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, label + "不能为空");
        }
        return String.valueOf(value).trim();
    }

    private String valueOr(Object value, String fallback) {
        return value == null || String.valueOf(value).isBlank() ? fallback : String.valueOf(value).trim();
    }

    private String text(Object value, String fallback) {
        return value == null ? fallback : String.valueOf(value).trim();
    }

    private String truncate(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }

    private int n(String sql, Object... args) {
        Integer value = jdbc.queryForObject(sql, Integer.class, args);
        return value == null ? 0 : value;
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return hex(bytes);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 计算失败", e);
        }
    }

    private String hmacSha256(String secret, String input) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return hex(mac.doFinal(input.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("HMAC 计算失败", e);
        }
    }

    private String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private void audit(String operator, String action, String targetId, String detail) {
        jdbc.update("INSERT INTO audit_log(action,target_type,target_id,operator,detail) VALUES (?,?,?,?,?)",
                action, "DATA_HUB", targetId, operator == null ? "system" : operator, detail);
    }
}
