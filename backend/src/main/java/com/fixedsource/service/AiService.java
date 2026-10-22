package com.fixedsource.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AiService {

    private static final Logger log = LoggerFactory.getLogger(AiService.class);
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private List<Reg> regulations = List.of();

    public AiService(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    private record Reg(String id, String law, String clause, String title, String effectiveDate, String text,
                       List<String> ruleCodes, List<String> pollutants) {}

    @PostConstruct
    public void loadKnowledge() {
        try (InputStream in = new ClassPathResource("knowledge/regulations.json").getInputStream()) {
            JsonNode root = objectMapper.readTree(in);
            List<Reg> list = new ArrayList<>();
            for (JsonNode n : root) {
                List<String> rules = new ArrayList<>();
                n.path("ruleCodes").forEach(x -> rules.add(x.asText()));
                List<String> pollutants = new ArrayList<>();
                n.path("pollutants").forEach(x -> pollutants.add(x.asText()));
                list.add(new Reg(n.path("id").asText(), n.path("law").asText(), n.path("clause").asText(),
                        n.path("title").asText(), n.path("effectiveDate").asText(), n.path("text").asText(),
                        rules, pollutants));
            }
            regulations = list;
            log.info("Loaded {} regulation snippets for mock RAG", regulations.size());
        } catch (Exception e) {
            log.error("Failed to load regulations", e);
        }
    }

    public int assessNewClues(LocalDate date) {
        List<Long> ids = jdbc.queryForList("""
                SELECT id FROM clue WHERE start_time >= ? AND start_time < ? AND ai_status='NONE' AND status <> 'REVOKED'
                ORDER BY id
                """, Long.class, Timestamp.valueOf(date.atStartOfDay()), Timestamp.valueOf(date.plusDays(1).atStartOfDay()));
        int done = 0;
        for (Long id : ids) {
            try {
                assessClue(id);
                done++;
            } catch (Exception e) {
                log.warn("AI assessment failed for clue {}: {}", id, e.getMessage());
            }
        }
        return done;
    }

    public Map<String, Object> assessClue(long clueId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT c.*, e.name AS enterpriseName, p.point_name AS pointName, p.outlet_name AS outletName,
                       po.name AS pollutantName, po.unit AS unit, po.limit_value AS limitValue,
                       po.standard_ref AS standardRef
                FROM clue c
                LEFT JOIN enterprise e ON e.id=c.enterprise_id
                LEFT JOIN monitor_point p ON p.id=c.point_id
                LEFT JOIN pollutant po ON po.code=c.pollutant_code
                WHERE c.id=?
                """, clueId);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("线索不存在");
        }
        Map<String, Object> c = rows.get(0);
        String ruleCode = String.valueOf(c.get("RULE_CODE"));
        String ruleName = String.valueOf(c.get("RULE_NAME"));
        String pollutant = String.valueOf(c.get("POLLUTANT_CODE"));
        String pollutantName = String.valueOf(c.get("POLLUTANTNAME"));
        String enterpriseName = String.valueOf(c.get("ENTERPRISENAME"));
        String pointName = String.valueOf(c.get("POINTNAME"));
        double trigger = c.get("TRIGGER_VALUE") == null ? 0 : ((Number) c.get("TRIGGER_VALUE")).doubleValue();
        double threshold = c.get("THRESHOLD_VALUE") == null ? 0 : ((Number) c.get("THRESHOLD_VALUE")).doubleValue();
        Timestamp startTs = (Timestamp) c.get("START_TIME");
        Timestamp endTs = (Timestamp) c.get("END_TIME");
        LocalDateTime start = startTs.toLocalDateTime();
        LocalDateTime end = endTs.toLocalDateTime();

        List<String> evidenceValues = new ArrayList<>();
        try {
            String evidenceJson = c.get("EVIDENCE_JSON") == null ? "{}" : String.valueOf(c.get("EVIDENCE_JSON"));
            Map<String, Object> evidence = objectMapper.readValue(evidenceJson, new TypeReference<>() {});
            Object values = evidence.get("values");
            if (values instanceof List<?> list) {
                for (Object o : list) evidenceValues.add(String.valueOf(o));
            }
        } catch (Exception ignored) {
        }

        List<Reg> matches = regulations.stream()
                .filter(r -> r.ruleCodes().contains(ruleCode) && r.pollutants().contains(pollutant))
                .limit(3)
                .collect(Collectors.toList());
        boolean refusal = matches.isEmpty();
        List<Map<String, Object>> citations = new ArrayList<>();
        for (Reg r : matches) {
            Map<String, Object> citation = new LinkedHashMap<>();
            citation.put("id", r.id());
            citation.put("law", r.law());
            citation.put("clause", r.clause());
            citation.put("title", r.title());
            citation.put("effectiveDate", r.effectiveDate());
            citation.put("quote", r.text());
            citations.add(citation);
        }

        String summary = enterpriseName + " · " + pointName + " · " + pollutantName + " " + ruleName;
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("summary", summary);
        content.put("ruleCode", ruleCode);
        content.put("ruleName", ruleName);
        content.put("timeRange", start.format(FMT) + " 至 " + end.format(FMT));
        content.put("triggerValue", trigger);
        content.put("thresholdValue", threshold);
        if (refusal) {
            content.put("explanation", "规则 " + ruleCode + " 在 " + start.format(FMT) + " 至 " + end.format(FMT)
                    + " 命中，触发值 " + fmt(trigger) + "，阈值 " + fmt(threshold)
                    + "。当前知识库未检索到与该污染物和规则直接对应的法规条款，不能据此生成确定性结论。");
            content.put("suggestions", List.of(
                    "补充该污染物对应的排放标准或技术规范条款",
                    "人工核查自动监测设备校准、运维和工况记录",
                    "必要时开展现场采样比对并留存证据"
            ));
            content.put("uncertainty", "证据不足：未检索到可引用条款，建议人工补充依据后再研判。");
        } else {
            content.put("explanation", "规则 " + ruleCode + " 在 " + start.format(FMT) + " 至 " + end.format(FMT)
                    + " 命中，触发值 " + fmt(trigger) + "，阈值 " + fmt(threshold)
                    + "。结合已检索的法规条款，该异常需要核实监测数据有效性、治理设施运行情况和现场工况，"
                    + "不能仅凭自动监测结果直接认定违法。");
            content.put("suggestions", suggestions(ruleCode));
            content.put("uncertainty", matches.size() < 2
                    ? "当前仅检索到少量依据，建议人工补充同行业标准或地方标准后复核。"
                    : "本结论基于模拟监测数据和公开法规片段，需由工作人员结合现场核查确认。");
        }
        content.put("evidence", evidenceValues);
        content.put("citations", citations);
        content.put("refusal", refusal);

        double confidence = refusal ? 0.18 : Math.min(0.88, 0.58 + 0.08 * citations.size());
        try {
            String contentJson = objectMapper.writeValueAsString(content);
            String citationsJson = objectMapper.writeValueAsString(citations);
            jdbc.update("""
                    INSERT INTO ai_assessment(clue_id,model,prompt_version,content_json,citations_json,confidence,refusal)
                    VALUES (?,?,?,?,?,?,?)
                    """, clueId, "mock-rag-v1", "p1.0", contentJson, citationsJson, confidence, refusal);
            Integer current = jdbc.queryForObject("SELECT credibility FROM clue WHERE id=?", Integer.class, clueId);
            int credibility = Math.max(0, Math.min(100, (current == null ? 60 : current) + (refusal ? -12 : 8)));
            String credibilityJson = "{}";
            try {
                Object raw = c.get("CREDIBILITY_JSON");
                Map<String, Object> cred = raw == null ? new LinkedHashMap<>()
                        : objectMapper.readValue(String.valueOf(raw), new TypeReference<>() {});
                cred.put("aiCitationCoverage", refusal ? 0 : 100);
                cred.put("credibilityAfterAi", credibility);
                credibilityJson = objectMapper.writeValueAsString(cred);
            } catch (Exception ignored) {
            }
            jdbc.update("UPDATE clue SET ai_status='DONE', credibility=?, credibility_json=? WHERE id=?",
                    credibility, credibilityJson, clueId);
            content.put("confidence", confidence);
            content.put("model", "mock-rag-v1");
            content.put("promptVersion", "p1.0");
            content.put("credibility", credibility);
            return content;
        } catch (Exception e) {
            throw new IllegalStateException("保存 AI 研判失败: " + e.getMessage(), e);
        }
    }

    public Map<String, Object> latestForClue(long clueId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT id, model, prompt_version AS promptVersion, content_json AS contentJson,
                       citations_json AS citationsJson, confidence, refusal,
                       FORMATDATETIME(created_at,'yyyy-MM-dd HH:mm:ss') AS createdAt
                FROM ai_assessment WHERE clue_id=? ORDER BY id DESC LIMIT 1
                """, clueId);
        if (rows.isEmpty()) return null;
        Map<String, Object> row = rows.get(0);
        Map<String, Object> result = new LinkedHashMap<>(row);
        try {
            result.put("content", objectMapper.readValue(String.valueOf(row.get("contentJson")), new TypeReference<>() {}));
            result.put("citations", objectMapper.readValue(String.valueOf(row.get("citationsJson")), new TypeReference<>() {}));
        } catch (Exception ignored) {
        }
        result.remove("contentJson");
        result.remove("citationsJson");
        return result;
    }

    private List<String> suggestions(String ruleCode) {
        return switch (ruleCode) {
            case "R001" -> List.of(
                    "核查异常时段生产负荷、治理设施运行记录和自动监测校准记录",
                    "复核监测设备量程、采样管路和比对试验结果",
                    "必要时开展现场采样比对，固定超标证据");
            case "R002" -> List.of(
                    "核查数采仪、传输链路和设备运行状态，确认缺失原因",
                    "调取缺失时段生产记录、运维记录和补录凭证",
                    "检查停运、故障标记是否与实际一致");
            case "R003" -> List.of(
                    "核查传感器状态、量程设置、校准和运维记录",
                    "比对企业历史数据、工况和治理设施运行情况",
                    "排查数据造假、设备故障或参数被固定的可能");
            case "R004" -> List.of(
                    "核查突变前后生产工况、治理设施启停和仪表状态",
                    "复核自动监测设备校准、比对和故障记录",
                    "必要时调取原始报文和现场照片核对");
            case "R005" -> List.of(
                    "核实生产设施、治理设施和监测设备的实际运行状态",
                    "检查停运、故障、校准标记是否与台账一致",
                    "对矛盾时段开展现场核查并留存影像证据");
            default -> List.of("核查监测数据有效性", "核实现场工况和治理设施运行情况");
        };
    }

    private String fmt(double v) {
        return String.format("%.2f", v);
    }
}
