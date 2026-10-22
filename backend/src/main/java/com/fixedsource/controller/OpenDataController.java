package com.fixedsource.controller;

import com.fixedsource.service.DataHubService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 国家数据仓的开放数据接口，供订阅方主动拉取最新数据包。
 * 使用独立的 X-Open-Data-Key，不与业务平台用户 token 混用。
 */
@RestController
@RequestMapping("/open-data/v1")
public class OpenDataController {

    private final DataHubService dataHubService;
    private final String openKey;

    public OpenDataController(DataHubService dataHubService,
                              @Value("${app.data-hub.open-key:national-open-demo-key}") String openKey) {
        this.dataHubService = dataHubService;
        this.openKey = openKey;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "UP");
        result.put("service", "national-eco-data-repository");
        result.put("time", LocalDateTime.now().toString());
        return result;
    }

    @GetMapping("/catalog")
    public Map<String, Object> catalog(
            @RequestHeader(value = "X-Open-Data-Key", required = false) String headerKey,
            @RequestParam(value = "key", required = false) String queryKey) {
        requireKey(headerKey, queryKey);
        return dataHubService.openCatalog();
    }

    @GetMapping("/datasets/{code}/latest")
    public Map<String, Object> latest(@PathVariable String code,
                                      @RequestHeader(value = "X-Open-Data-Key", required = false) String headerKey,
                                      @RequestParam(value = "key", required = false) String queryKey) {
        requireKey(headerKey, queryKey);
        return dataHubService.openLatest(code);
    }

    private void requireKey(String headerKey, String queryKey) {
        String actual = headerKey != null && !headerKey.isBlank() ? headerKey : queryKey;
        if (actual == null || !openKey.equals(actual)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "开放数据 Key 无效");
        }
    }
}
