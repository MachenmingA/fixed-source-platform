package com.fixedsource.controller;

import com.fixedsource.service.DataHubService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 模拟国家部委/地方平台的数据接收端。
 * 验证 AppKey、时间戳和 HMAC-SHA256 签名，并写入回执。
 */
@RestController
public class MockConsumerController {

    private final DataHubService dataHubService;

    public MockConsumerController(DataHubService dataHubService) {
        this.dataHubService = dataHubService;
    }

    @PostMapping("/mock-consumer/v1/{system}/ingest")
    public Map<String, Object> ingest(@PathVariable String system,
                                      @RequestBody String rawBody,
                                      @RequestHeader(value = "X-DataHub-App-Key", required = false) String appKey,
                                      @RequestHeader(value = "X-DataHub-Timestamp", required = false) String timestamp,
                                      @RequestHeader(value = "X-DataHub-Signature", required = false) String signature) {
        return dataHubService.receive(system, rawBody, appKey, timestamp, signature);
    }
}
