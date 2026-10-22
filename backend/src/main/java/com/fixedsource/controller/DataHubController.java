package com.fixedsource.controller;

import com.fixedsource.service.AuthService;
import com.fixedsource.service.DataHubService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/data-hub")
public class DataHubController {

    private final DataHubService dataHubService;
    private final AuthService authService;

    public DataHubController(DataHubService dataHubService, AuthService authService) {
        this.dataHubService = dataHubService;
        this.authService = authService;
    }

    @GetMapping("/overview")
    public Map<String, Object> overview() {
        return dataHubService.overview();
    }

    @GetMapping("/packages")
    public List<Map<String, Object>> packages(@RequestParam(defaultValue = "30") int limit) {
        return dataHubService.packageRows(limit);
    }

    @GetMapping("/packages/{id}")
    public Map<String, Object> packageDetail(@PathVariable long id) {
        Map<String, Object> result = dataHubService.packageDetail(id);
        if (result == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "数据包不存在");
        return result;
    }

    @GetMapping("/packages/{id}/payload")
    public ResponseEntity<byte[]> payload(@PathVariable long id) {
        Map<String, Object> detail = dataHubService.packageDetail(id);
        if (detail == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "数据包不存在");
        byte[] bytes = dataHubService.packagePayload(id);
        String fileName = String.valueOf(detail.get("packageNo")) + ".json";
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(fileName, StandardCharsets.UTF_8).build().toString())
                .body(bytes);
    }

    @GetMapping("/dispatches")
    public List<Map<String, Object>> dispatches(@RequestParam(defaultValue = "50") int limit,
                                                 @RequestParam(required = false) String status) {
        return dataHubService.dispatchRows(limit, status);
    }

    @PostMapping("/packages/publish")
    public Map<String, Object> publish(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        AuthService.User user = requireMaintainer(request);
        String datasetCode = body.get("datasetCode") == null ? null : String.valueOf(body.get("datasetCode"));
        String bizDate = body.get("bizDate") == null ? null : String.valueOf(body.get("bizDate"));
        return dataHubService.publish(datasetCode, bizDate, user.name());
    }

    @PostMapping("/packages/publish-all")
    public Map<String, Object> publishAll(@RequestBody(required = false) Map<String, Object> body,
                                          HttpServletRequest request) {
        AuthService.User user = requireMaintainer(request);
        List<String> codes = body != null && body.get("datasetCodes") instanceof List<?> list
                ? list.stream().map(String::valueOf).toList()
                : List.of("ENTERPRISE_MASTER", "MONITORING_HOURLY", "ABNORMAL_EVENT", "RISK_PROFILE", "TASK_CLOSURE");
        String bizDate = body == null || body.get("bizDate") == null ? null : String.valueOf(body.get("bizDate"));
        return dataHubService.publishMany(codes, bizDate, user.name());
    }

    @PostMapping("/dispatches/{id}/retry")
    public Map<String, Object> retry(@PathVariable long id, HttpServletRequest request) {
        AuthService.User user = requireMaintainer(request);
        return dataHubService.retry(id, user.name());
    }

    @GetMapping("/subscribers")
    public List<Map<String, Object>> subscribers() {
        return dataHubService.subscriberRows();
    }

    @PostMapping("/subscribers")
    public Map<String, Object> createSubscriber(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        AuthService.User user = requireMaintainer(request);
        return dataHubService.createSubscriber(body, user.name());
    }

    @PutMapping("/subscribers/{id}")
    public Map<String, Object> updateSubscriber(@PathVariable long id, @RequestBody Map<String, Object> body,
                                                HttpServletRequest request) {
        AuthService.User user = requireMaintainer(request);
        return dataHubService.updateSubscriber(id, body, user.name());
    }

    private AuthService.User requireMaintainer(HttpServletRequest request) {
        AuthService.User user = (AuthService.User) request.getAttribute("user");
        if (!authService.hasAnyRole(user, "reviewer", "admin")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只有审核人员或管理员可以发布数据包与维护订阅方");
        }
        return user;
    }
}
