package com.fixedsource.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * 国家数据仓访问业务平台标准数据服务出口的客户端。
 * 走真实 HTTP + 服务账号 token，模拟跨平台接口对接，而不是直接读业务库。
 */
@Service
public class DataHubSourceClient {

    private static final Logger log = LoggerFactory.getLogger(DataHubSourceClient.class);

    private final ObjectMapper objectMapper;
    private final RestClient client;
    private volatile String token;

    public DataHubSourceClient(ObjectMapper objectMapper,
                               @Value("${app.data-hub.source-base:http://127.0.0.1:8080}") String baseUrl) {
        this.objectMapper = objectMapper;
        this.client = RestClient.builder().baseUrl(baseUrl).build();
    }

    public JsonNode export(String datasetCode, String bizDate, boolean retry) {
        String uri = "/api/data-service/v1/export/" + datasetCode
                + (bizDate == null || bizDate.isBlank() ? "" : "?bizDate=" + bizDate);
        try {
            return client.get()
                    .uri(uri)
                    .header("X-Token", token())
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (org.springframework.web.client.HttpClientErrorException.Unauthorized e) {
            if (!retry) {
                token = null;
                return export(datasetCode, bizDate, true);
            }
            throw e;
        }
    }

    private String token() {
        String current = token;
        if (current != null && !current.isBlank()) return current;
        synchronized (this) {
            if (token != null && !token.isBlank()) return token;
            try {
                JsonNode login = client.post()
                        .uri("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("username", "datahub", "password", "123456"))
                        .retrieve()
                        .body(JsonNode.class);
                if (login == null || login.path("token").asText().isBlank()) {
                    throw new IllegalStateException("数据仓服务账号登录失败");
                }
                token = login.path("token").asText();
                return token;
            } catch (Exception e) {
                log.error("Data hub source login failed: {}", e.getMessage());
                throw new IllegalStateException("无法连接业务平台数据服务： " + e.getMessage(), e);
            }
        }
    }
}
