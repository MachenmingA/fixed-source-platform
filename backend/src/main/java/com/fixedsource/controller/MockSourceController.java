package com.fixedsource.controller;

import com.fixedsource.service.MockSourceService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/mock-source/v1")
public class MockSourceController {

    private final MockSourceService mockSource;

    public MockSourceController(MockSourceService mockSource) {
        this.mockSource = mockSource;
    }

    @GetMapping("/base-data")
    public Map<String, Object> baseData() {
        return mockSource.baseDataPayload();
    }

    @GetMapping("/monitoring")
    public Map<String, Object> monitoring(@RequestParam(required = false) String bizDate) {
        LocalDate date = bizDate == null || bizDate.isBlank() ? LocalDate.now().minusDays(1) : LocalDate.parse(bizDate);
        return mockSource.monitoringPayload(date);
    }
}
