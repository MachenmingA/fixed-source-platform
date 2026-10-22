package com.fixedsource;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DataHubFlowTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void dataRepositoryCatalogAndSignedReceipt() throws Exception {
        String reviewer = login("reviewer");
        mockMvc.perform(get("/api/data-hub/overview").header("X-Token", reviewer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.catalogCount").value(7))
                .andExpect(jsonPath("$.subscriberCount").value(5))
                .andExpect(jsonPath("$.enabledSubscriberCount").value(4));

        mockMvc.perform(post("/api/data-hub/packages/publish")
                        .header("X-Token", login("analyst"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"datasetCode\":\"ENTERPRISE_MASTER\"}"))
                .andExpect(status().isForbidden());

        String datahub = login("datahub");
        mockMvc.perform(get("/api/data-service/v1/export/ENTERPRISE_MASTER").header("X-Token", datahub))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datasetCode").value("ENTERPRISE_MASTER"))
                .andExpect(jsonPath("$.recordCount").value(10))
                .andExpect(jsonPath("$.records.length()").value(10))
                .andExpect(jsonPath("$.records[0].points").isArray());

        mockMvc.perform(get("/open-data/v1/catalog"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/open-data/v1/catalog").header("X-Open-Data-Key", "national-open-demo-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.catalogs.length()").value(7))
                .andExpect(jsonPath("$.repository").value("国家生态环境公共数据信息库"));

        String secret = jdbc.queryForObject(
                "SELECT app_secret FROM data_subscriber WHERE code='NPE'", String.class);
        String packageNo = "DP-TEST-" + UUID.randomUUID().toString().substring(0, 8);
        Map<String, Object> header = new LinkedHashMap<>();
        header.put("packageNo", packageNo);
        header.put("datasetCode", "ENTERPRISE_MASTER");
        header.put("datasetName", "企业主数据");
        header.put("recordCount", 1);
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("header", header);
        envelope.put("body", Map.of("records", java.util.List.of(Map.of("id", "E001", "name", "测试企业"))));
        String rawBody = objectMapper.writeValueAsString(envelope);
        String timestamp = String.valueOf(System.currentTimeMillis());
        String signature = hmac(secret, timestamp + "\n" + packageNo + "\n" + sha256(rawBody));

        mockMvc.perform(post("/mock-consumer/v1/npe/ingest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-DataHub-App-Key", "npe-open-key")
                        .header("X-DataHub-Timestamp", timestamp)
                        .header("X-DataHub-Signature", signature)
                        .header("X-DataHub-Package-No", packageNo)
                        .content(rawBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accepted").value(true))
                .andExpect(jsonPath("$.validation").value("HMAC-SHA256"));

        mockMvc.perform(post("/mock-consumer/v1/npe/ingest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-DataHub-App-Key", "npe-open-key")
                        .header("X-DataHub-Timestamp", timestamp)
                        .header("X-DataHub-Signature", "invalid-signature")
                        .content(rawBody))
                .andExpect(status().isUnauthorized());

        Integer receipts = jdbc.queryForObject("""
                SELECT COUNT(*) FROM data_receipt WHERE package_no=? AND subscriber_code='NPE'
                """, Integer.class, packageNo);
        assertThat(receipts).isEqualTo(1);
    }

    private String login(String username) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username, "password", "123456"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("token").asText();
    }

    private String sha256(String input) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return hex(digest.digest(input.getBytes(StandardCharsets.UTF_8)));
    }

    private String hmac(String secret, String input) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return hex(mac.doFinal(input.getBytes(StandardCharsets.UTF_8)));
    }

    private String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
