package com.fixedsource.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class ReportService {

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final EventRiskService eventRiskService;

    public ReportService(JdbcTemplate jdbc, ObjectMapper objectMapper, EventRiskService eventRiskService) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.eventRiskService = eventRiskService;
    }

    public Map<String, Object> generate(long eventId, String operator) {
        Map<String, Object> detail = eventRiskService.getEvent(eventId);
        if (detail == null) throw new IllegalArgumentException("事件不存在");
        Map<String, Object> event = castMap(detail.get("event"));
        List<Map<String, Object>> clues = castList(detail.get("clues"));
        List<Map<String, Object>> tasks = castList(detail.get("tasks"));

        String reportNo = "RP" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + String.format("%03d", ThreadLocalRandom.current().nextInt(1000));
        String title = "固定污染源异常线索核查报告 · " + event.get("title");
        StringBuilder html = new StringBuilder();
        html.append("<div class=\"report-doc\">");
        html.append("<div class=\"report-head\"><div><div class=\"report-label\">FIXED SOURCE INTELLIGENCE</div>")
                .append("<h1>固定污染源异常线索核查报告</h1>")
                .append("<p>报告编号 ").append(esc(reportNo)).append(" · 生成时间 ")
                .append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))).append("</p></div>")
                .append("<div class=\"report-status\">辅助生成<br/><b>需人工审核</b></div></div>");

        html.append("<h2>一、企业基本情况</h2><table class=\"kv\">")
                .append(row("企业名称", event.get("enterpriseName")))
                .append(row("行业 / 区域", event.get("industry") + " / " + event.get("region")))
                .append(row("风险等级", event.get("riskLevel")))
                .append(row("监测点位", event.get("pointName")))
                .append(row("污染物", event.get("pollutantName") + "（" + event.get("unit") + "）"))
                .append(row("适用标准", event.get("standardRef")))
                .append("</table>");

        html.append("<h2>二、异常事件</h2><table class=\"kv\">")
                .append(row("事件编号", event.get("eventNo")))
                .append(row("事件标题", event.get("title")))
                .append(row("发生时间", event.get("startTime") + " 至 " + event.get("endTime")))
                .append(row("优先级 / 严重度", event.get("priority") + " / " + event.get("maxSeverity")))
                .append(row("可信度", event.get("credibility")))
                .append(row("关联线索", event.get("clueCount") + " 条"))
                .append("</table>");

        html.append("<h2>三、证据链</h2>");
        for (Map<String, Object> clue : clues) {
            html.append("<div class=\"clue\"><div class=\"clue-title\">")
                    .append(esc(clue.get("ruleCode"))).append(" ").append(esc(clue.get("ruleName")))
                    .append(" <span>").append(esc(clue.get("startTime"))).append(" 至 ")
                    .append(esc(clue.get("endTime"))).append("</span></div>")
                    .append("<p>触发值 <b>").append(fmt(clue.get("triggerValue"))).append("</b>，阈值 <b>")
                    .append(fmt(clue.get("thresholdValue"))).append("</b>，可信度 <b>")
                    .append(clue.get("credibility")).append("</b></p>");
            Map<String, Object> evidence = castMap(clue.get("evidence"));
            List<?> values = evidence.get("values") instanceof List<?> list ? list : List.of();
            if (!values.isEmpty()) {
                html.append("<ul>");
                for (Object value : values) html.append("<li>").append(esc(value)).append("</li>");
                html.append("</ul>");
            }
            html.append("</div>");
        }

        html.append("<h2>四、AI 辅助研判与引用依据</h2>");
        List<Map<String, Object>> suggestions = new ArrayList<>();
        for (Map<String, Object> clue : clues) {
            Map<String, Object> ai = castMap(clue.get("ai"));
            if (ai.isEmpty()) {
                html.append("<div class=\"clue\"><div class=\"clue-title\">").append(esc(clue.get("ruleName")))
                        .append("</div><p class=\"muted\">尚未生成 AI 研判。</p></div>");
                continue;
            }
            Map<String, Object> content = castMap(ai.get("content"));
            html.append("<div class=\"ai-block\"><div class=\"clue-title\">")
                    .append(esc(clue.get("ruleName"))).append(" · AI 研判</div>");
            if (Boolean.TRUE.equals(ai.get("refusal")) || Boolean.TRUE.equals(content.get("refusal"))) {
                html.append("<p class=\"refusal\">未检索到可引用条款，AI 已拒答，需人工补充依据。</p>");
            } else {
                html.append("<p>").append(esc(content.get("explanation"))).append("</p>");
            }
            List<Map<String, Object>> citations = castList(ai.get("citations"));
            if (!citations.isEmpty()) {
                html.append("<div class=\"citations\">");
                for (Map<String, Object> citation : citations) {
                    html.append("<div class=\"citation\"><b>").append(esc(citation.get("law"))).append(" ")
                            .append(esc(citation.get("clause"))).append("</b><span>")
                            .append(esc(citation.get("title"))).append(" · 生效 ")
                            .append(esc(citation.get("effectiveDate"))).append("</span><p>")
                            .append(esc(citation.get("quote"))).append("</p></div>");
                }
                html.append("</div>");
            }
            List<?> suggestionList = content.get("suggestions") instanceof List<?> list ? list : List.of();
            for (Object suggestion : suggestionList) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("ruleName", clue.get("ruleName"));
                item.put("suggestion", suggestion);
                suggestions.add(item);
            }
            html.append("</div>");
        }

        html.append("<h2>五、建议核查内容</h2><ul class=\"suggestions\">");
        if (suggestions.isEmpty()) {
            html.append("<li>结合现场工况和监测设备运维记录进一步核查。</li>");
        } else {
            for (Map<String, Object> item : suggestions) {
                html.append("<li>[").append(esc(item.get("ruleName"))).append("] ")
                        .append(esc(item.get("suggestion"))).append("</li>");
            }
        }
        html.append("</ul>");

        html.append("<h2>六、核查任务与现场反馈</h2>");
        if (tasks.isEmpty()) {
            html.append("<p class=\"muted\">尚未创建核查任务。</p>");
        } else {
            for (Map<String, Object> task : tasks) {
                html.append("<div class=\"task\"><div class=\"clue-title\">").append(esc(task.get("taskNo")))
                        .append(" · ").append(esc(statusLabel(task.get("status")))).append("</div>");
                List<Map<String, Object>> feedback = jdbc.queryForList("""
                        SELECT feedback_no AS feedbackNo, inspector, result, problem, disposal, watermark,
                               FORMATDATETIME(submitted_at,'yyyy-MM-dd HH:mm') AS submittedAt
                        FROM task_feedback WHERE task_id=? ORDER BY id DESC LIMIT 1
                        """, task.get("id"));
                if (feedback.isEmpty()) {
                    html.append("<p class=\"muted\">暂未提交现场反馈。</p>");
                } else {
                    Map<String, Object> fb = feedback.get(0);
                    html.append("<p>核查人员：").append(esc(fb.get("inspector"))).append(" · 提交时间：")
                            .append(esc(fb.get("submittedAt"))).append("</p>")
                            .append("<p>发现问题：").append(esc(fb.get("problem"))).append("</p>")
                            .append("<p>处置情况：").append(esc(fb.get("disposal"))).append("</p>");
                    if (fb.get("watermark") != null) {
                        html.append("<p class=\"watermark\">取证水印：").append(esc(fb.get("watermark"))).append("</p>");
                    }
                }
                html.append("</div>");
            }
        }
        html.append("<p class=\"disclaimer\">本报告由系统根据模拟监测数据、规则证据、公开法规片段和 AI 辅助研判生成，仅用于毕业设计演示。报告内容需由工作人员复核，不替代现场核查和行政执法决定。</p>");
        html.append("</div>");

        Map<String, Object> contentJson = new LinkedHashMap<>();
        contentJson.put("event", event);
        contentJson.put("clues", clues);
        contentJson.put("suggestions", suggestions);
        contentJson.put("tasks", tasks);
        try {
            jdbc.update("""
                    INSERT INTO inspection_report(report_no,event_id,title,content_html,content_json,status,created_by)
                    VALUES (?,?,?,?,?,?,?)
                    """, reportNo, eventId, title, html.toString(), objectMapper.writeValueAsString(contentJson),
                    "DRAFT", operator);
        } catch (Exception e) {
            throw new IllegalStateException("保存报告失败: " + e.getMessage(), e);
        }
        Long id = jdbc.queryForObject("SELECT id FROM inspection_report WHERE report_no=?", Long.class, reportNo);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", id);
        result.put("reportNo", reportNo);
        result.put("title", title);
        result.put("contentHtml", html.toString());
        result.put("status", "DRAFT");
        return result;
    }

    public List<Map<String, Object>> list() {
        return jdbc.queryForList("""
                SELECT r.id, r.report_no AS reportNo, r.event_id AS eventId, r.title, r.status,
                       r.created_by AS createdBy, FORMATDATETIME(r.created_at,'yyyy-MM-dd HH:mm') AS createdAt,
                       e.name AS enterpriseName, ev.event_no AS eventNo
                FROM inspection_report r
                LEFT JOIN abnormal_event ev ON ev.id=r.event_id
                LEFT JOIN enterprise e ON e.id=ev.enterprise_id
                ORDER BY r.id DESC LIMIT 100
                """);
    }

    public Map<String, Object> get(long id) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT r.id, r.report_no AS reportNo, r.event_id AS eventId, r.title,
                       r.content_html AS contentHtml, r.status, r.created_by AS createdBy,
                       FORMATDATETIME(r.created_at,'yyyy-MM-dd HH:mm') AS createdAt,
                       e.name AS enterpriseName, ev.event_no AS eventNo
                FROM inspection_report r
                LEFT JOIN abnormal_event ev ON ev.id=r.event_id
                LEFT JOIN enterprise e ON e.id=ev.enterprise_id
                WHERE r.id=?
                """, id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> castList(Object value) {
        return value instanceof List<?> list ? (List<Map<String, Object>>) list : List.of();
    }

    private String row(String label, Object value) {
        return "<tr><th>" + esc(label) + "</th><td>" + esc(value) + "</td></tr>";
    }

    private String fmt(Object value) {
        if (value == null) return "—";
        try {
            return String.format("%.2f", Double.parseDouble(String.valueOf(value)));
        } catch (Exception e) {
            return esc(value);
        }
    }

    private String statusLabel(Object value) {
        String status = value == null ? "" : String.valueOf(value);
        return switch (status) {
            case "PENDING_DISPATCH" -> "待下发";
            case "DISPATCHED" -> "已下发";
            case "IN_PROGRESS" -> "执行中";
            case "FEEDBACK" -> "已反馈";
            case "CLOSED" -> "已关闭";
            case "DISPATCH_FAILED" -> "下发失败";
            default -> status;
        };
    }

    private String esc(Object value) {
        if (value == null) return "";
        return String.valueOf(value)
                .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
}
