package com.fixedsource.config;

import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * H2/JdbcTemplate returns column aliases in upper case. Normalize them to camelCase
 * so the Vue front end can use one naming convention for both SQL rows and computed maps.
 */
@RestControllerAdvice
public class ResponseKeyAdvice implements ResponseBodyAdvice<Object> {

    private static final Map<String, String> COMPOUND = new LinkedHashMap<>();

    static {
        String[][] pairs = {
                {"RISKSCORE", "riskScore"}, {"RISKLEVEL", "riskLevel"}, {"MANAGEMENTLEVEL", "managementLevel"},
                {"CREDITCODE", "creditCode"}, {"POINTCOUNT", "pointCount"}, {"PENDINGEVENTS", "pendingEvents"},
                {"LEGALREPRESENTATIVE", "legalRepresentative"}, {"CONTACTNAME", "contactName"},
                {"CONTACTPHONE", "contactPhone"}, {"PRODUCTIONSTATUS", "productionStatus"},
                {"PERMITNO", "permitNo"}, {"LASTMONITORTIME", "lastMonitorTime"},
                {"ACTIVECOUNT", "activeCount"}, {"HIGHRISKCOUNT", "highRiskCount"},
                {"ACTIVEENTERPRISECOUNT", "activeEnterpriseCount"}, {"INACTIVEENTERPRISECOUNT", "inactiveEnterpriseCount"},
                {"OUTLETNAME", "outletName"}, {"POINTNAME", "pointName"}, {"POINTTYPE", "pointType"},
                {"POLLUTANTCOUNT", "pollutantCount"}, {"FACTORSJSON", "factorsJson"}, {"CREATEDAT", "createdAt"},
                {"STARTTIME", "startTime"}, {"ENDTIME", "endTime"}, {"EVENTNO", "eventNo"},
                {"MAXSEVERITY", "maxSeverity"}, {"CLUECOUNT", "clueCount"}, {"ENTERPRISENAME", "enterpriseName"},
                {"ENTERPRISEID", "enterpriseId"}, {"POLLUTANTNAME", "pollutantName"}, {"POLLUTANTCODE", "pollutantCode"},
                {"STANDARDREF", "standardRef"}, {"LIMITVALUE", "limitValue"}, {"RULECODE", "ruleCode"},
                {"RULENAME", "ruleName"}, {"TRIGGERVALUE", "triggerValue"}, {"THRESHOLDVALUE", "thresholdValue"},
                {"AISTATUS", "aiStatus"}, {"CLUENO", "clueNo"}, {"BATCHNO", "batchNo"}, {"BIZDATE", "bizDate"},
                {"EXPECTEDCOUNT", "expectedCount"}, {"RECEIVEDCOUNT", "receivedCount"}, {"SUCCESSCOUNT", "successCount"},
                {"FAILEDCOUNT", "failedCount"}, {"STARTEDAT", "startedAt"}, {"FINISHEDAT", "finishedAt"},
                {"ORIGINALBATCHNO", "originalBatchNo"}, {"EXTERNALNO", "externalNo"}, {"SLASTATUS", "slaStatus"},
                {"SLAHOURS", "slaHours"},
                {"DUEAT", "dueAt"}, {"DISPATCHEDAT", "dispatchedAt"}, {"FEEDBACKAT", "feedbackAt"},
                {"CLOSEDAT", "closedAt"}, {"URGECOUNT", "urgeCount"}, {"TASKNO", "taskNo"},
                {"FEEDBACKNO", "feedbackNo"}, {"ATTACHMENTURL", "attachmentUrl"}, {"SUBMITTEDAT", "submittedAt"},
                {"WINDOWHOURS", "windowHours"}, {"THRESHOLDEXPR", "thresholdExpr"}, {"EFFECTIVEDATE", "effectiveDate"},
                {"BIZDAY", "bizDay"}, {"HIGHCOUNT", "highCount"}, {"AVGRISK", "avgRisk"},
                {"EVIDENCEJSON", "evidenceJson"}, {"CREDIBILITYJSON", "credibilityJson"}, {"CONTENTJSON", "contentJson"},
                {"CITATIONSJSON", "citationsJson"}, {"PROMPTVERSION", "promptVersion"}, {"AICONTENT", "aiContent"},
                {"AICITATIONS", "aiCitations"}, {"AIREFUSAL", "aiRefusal"}, {"TASKSTATUS", "taskStatus"},
                {"CLUEID", "clueId"}, {"EVENTID", "eventId"}, {"TASKID", "taskId"}, {"POINTID", "pointId"},
                {"RISKTREND", "riskTrend"}, {"RULESTATS", "ruleStats"}, {"RISKDISTRIBUTION", "riskDistribution"},
                {"EVENTTREND", "eventTrend"}, {"REGIONSTATS", "regionStats"}, {"RECENTEVENTS", "recentEvents"},
                {"RISKRANKING", "riskRanking"}, {"TASKOPEN", "taskOpen"}, {"TASKCLOSED", "taskClosed"},
                {"SLAOVERDUE", "slaOverdue"}, {"TOTALCLUES", "totalClues"}, {"AIDONE", "aiDone"},
                {"AIREFUSALCOUNT", "aiRefusalCount"}, {"RESULT", "result"},
                {"ANOMALYTYPE", "anomalyType"}, {"STARTHOUR", "startHour"}, {"ENDHOUR", "endHour"},
                {"EXPECTEDSEVERITY", "expectedSeverity"}, {"DETECTEDCOUNT", "detectedCount"},
                {"ISSUETYPE", "issueType"}, {"MONITORDATE", "monitorDate"}, {"SOURCETYPE", "sourceType"},
                {"BASEURL", "baseUrl"}, {"AUTHTYPE", "authType"}, {"UPDATEDAT", "updatedAt"},
                {"CREATEDBY", "createdBy"}, {"RULEVERSION", "ruleVersion"}, {"TOTALLABELS", "totalLabels"},
                {"TRUEPOSITIVE", "truePositive"}, {"FALSEPOSITIVE", "falsePositive"}, {"FALSENEGATIVE", "falseNegative"},
                {"PRECISIONVALUE", "precisionValue"}, {"RECALLVALUE", "recallValue"}, {"F1VALUE", "f1Value"},
                {"REPORTNO", "reportNo"}, {"CONTENTHTML", "contentHtml"}, {"MESSAGENO", "messageNo"},
                {"RETRYCOUNT", "retryCount"}, {"REQUESTJSON", "requestJson"}, {"RESPONSEJSON", "responseJson"},
                {"ENTERPRISECOUNT", "enterpriseCount"}, {"EVENTCOUNT", "eventCount"}, {"CLOSEDEVENTS", "closedEvents"},
                {"TASKCOUNT", "taskCount"}, {"CLOSEDTASKS", "closedTasks"}, {"HIGHRISK", "highRisk"},
                {"RUNNO", "runNo"}, {"LABELCOUNT", "labelCount"},
                {"STARTDATE", "startDate"}, {"ENDDATE", "endDate"}
        };
        for (String[] pair : pairs) COMPOUND.put(pair[0], pair[1]);
    }

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  ServerHttpRequest request, ServerHttpResponse response) {
        return convert(body);
    }

    private Object convert(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                result.put(normalize(String.valueOf(entry.getKey())), convert(entry.getValue()));
            }
            return result;
        }
        if (value instanceof List<?> list) {
            List<Object> result = new ArrayList<>(list.size());
            for (Object item : list) result.add(convert(item));
            return result;
        }
        return value;
    }

    private String normalize(String key) {
        if (key == null || key.isEmpty()) return key;
        if (key.contains("_")) {
            String[] parts = key.toLowerCase().split("_");
            StringBuilder sb = new StringBuilder(parts[0]);
            for (int i = 1; i < parts.length; i++) {
                if (!parts[i].isEmpty()) {
                    sb.append(Character.toUpperCase(parts[i].charAt(0))).append(parts[i].substring(1));
                }
            }
            return sb.toString();
        }
        if (key.equals(key.toUpperCase())) {
            return COMPOUND.getOrDefault(key, key.toLowerCase());
        }
        return key;
    }
}
