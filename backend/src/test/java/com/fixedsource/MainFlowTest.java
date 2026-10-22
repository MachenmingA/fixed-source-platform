package com.fixedsource;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MainFlowTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void mainChainEndToEnd() throws Exception {
        String analyst = login("analyst");
        mockMvc.perform(get("/api/dashboard/overview").header("X-Token", analyst))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enterpriseCount").value(10));
        mockMvc.perform(get("/api/test-data/labels").header("X-Token", analyst))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(28));

        LocalDate today = LocalDate.now();
        MvcResult backtest = mockMvc.perform(post("/api/backtests")
                        .header("X-Token", analyst)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "startDate", today.minusDays(7).toString(),
                                "endDate", today.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overall.labels").isNumber())
                .andReturn();
        Map<String, Object> backtestMap = read(backtest);
        assertThat(((Map<?, ?>) backtestMap.get("overall")).get("f1")).isNotNull();

        Long clueId = jdbc.queryForObject("""
                SELECT id FROM clue WHERE status='PENDING' AND rule_code LIKE 'R%'
                ORDER BY CASE severity WHEN 'HIGH' THEN 1 WHEN 'MEDIUM' THEN 2 ELSE 3 END LIMIT 1
                """, Long.class);
        assertThat(clueId).isNotNull();
        String reviewer = login("reviewer");
        mockMvc.perform(post("/api/clues/" + clueId + "/confirm")
                        .header("X-Token", reviewer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CONFIRMED\",\"note\":\"integration test\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        MvcResult taskResult = mockMvc.perform(post("/api/inspection-tasks")
                        .header("X-Token", reviewer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("clueId", clueId))))
                .andExpect(status().isOk())
                .andReturn();
        Map<String, Object> task = read(taskResult);
        String taskNo = String.valueOf(task.get("taskNo"));
        Number taskId = (Number) task.get("id");
        assertThat(taskNo).startsWith("TSK-");

        mockMvc.perform(post("/api/inspection-tasks/" + taskId + "/dispatch").header("X-Token", reviewer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISPATCHED"));

        String inspector = login("inspector");
        mockMvc.perform(post("/api/mobile/tasks/" + taskNo + "/accept").header("X-Token", inspector))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));

        Map<String, Object> feedback = new LinkedHashMap<>();
        feedback.put("inspector", "测试核查员");
        feedback.put("result", "ISSUE");
        feedback.put("problem", "集成测试：现场记录不完整");
        feedback.put("disposal", "要求整改并补充记录");
        feedback.put("watermark", "2026-10-21 11:00 测试点位");
        mockMvc.perform(post("/api/mobile/tasks/" + taskNo + "/feedback")
                        .header("X-Token", inspector)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(feedback)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FEEDBACK"))
                .andExpect(jsonPath("$.feedback.result").value("ISSUE"));

        mockMvc.perform(post("/api/inspection-tasks/" + taskId + "/close").header("X-Token", reviewer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"));

        MvcResult summary = mockMvc.perform(get("/api/test-data/summary")
                        .header("X-Token", analyst)
                        .param("startDate", today.minusDays(7).toString())
                        .param("endDate", today.toString()))
                .andExpect(status().isOk())
                .andReturn();
        Map<String, Object> summaryMap = read(summary);
        Map<?, ?> overall = (Map<?, ?>) summaryMap.get("overall");
        assertThat(((Number) overall.get("labels")).intValue()).isGreaterThan(0);
        assertThat(((Number) overall.get("f1")).doubleValue()).isGreaterThan(0.5);
    }

    private String login(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("username", username, "password", "123456"))))
                .andExpect(status().isOk())
                .andReturn();
        return String.valueOf(read(result).get("token"));
    }

    private Map<String, Object> read(MvcResult result) throws Exception {
        return objectMapper.readValue(result.getResponse().getContentAsString(), new TypeReference<>() {});
    }
}
