package com.fixedsource.controller;

import com.fixedsource.service.DataQualityService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/data-quality")
public class DataQualityController {

    private final DataQualityService dataQualityService;

    public DataQualityController(DataQualityService dataQualityService) {
        this.dataQualityService = dataQualityService;
    }

    @GetMapping
    public List<Map<String, Object>> list(@RequestParam(required = false) String startDate,
                                          @RequestParam(required = false) String endDate,
                                          @RequestParam(required = false) String issueType,
                                          @RequestParam(required = false) String severity) {
        LocalDate end = endDate == null || endDate.isBlank() ? LocalDate.now() : LocalDate.parse(endDate);
        LocalDate start = startDate == null || startDate.isBlank() ? end.minusDays(7) : LocalDate.parse(startDate);
        return dataQualityService.list(start, end, issueType, severity);
    }

    @GetMapping("/summary")
    public Map<String, Object> summary(@RequestParam(required = false) String startDate,
                                       @RequestParam(required = false) String endDate) {
        LocalDate end = endDate == null || endDate.isBlank() ? LocalDate.now() : LocalDate.parse(endDate);
        LocalDate start = startDate == null || startDate.isBlank() ? end.minusDays(7) : LocalDate.parse(startDate);
        return dataQualityService.summary(start, end);
    }
}
