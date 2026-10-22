package com.fixedsource.controller;

import com.fixedsource.service.AuthService;
import com.fixedsource.service.ReportService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @PostMapping("/api/events/{id}/report")
    public Map<String, Object> generate(@PathVariable long id, HttpServletRequest request) {
        AuthService.User user = (AuthService.User) request.getAttribute("user");
        return reportService.generate(id, user.name());
    }

    @GetMapping("/api/reports")
    public List<Map<String, Object>> list() {
        return reportService.list();
    }

    @GetMapping("/api/reports/{id}")
    public Map<String, Object> get(@PathVariable long id) {
        return reportService.get(id);
    }
}
