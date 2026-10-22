package com.fixedsource.controller;

import com.fixedsource.service.AuthService;
import com.fixedsource.service.BacktestService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/backtests")
public class BacktestController {

    private final BacktestService backtestService;

    public BacktestController(BacktestService backtestService) {
        this.backtestService = backtestService;
    }

    @PostMapping
    public Map<String, Object> run(@RequestBody(required = false) Map<String, Object> body, HttpServletRequest request) {
        AuthService.User user = (AuthService.User) request.getAttribute("user");
        LocalDate end = body == null || body.get("endDate") == null
                ? LocalDate.now() : LocalDate.parse(String.valueOf(body.get("endDate")));
        LocalDate start = body == null || body.get("startDate") == null
                ? end.minusDays(7) : LocalDate.parse(String.valueOf(body.get("startDate")));
        return backtestService.run(start, end, user.name());
    }

    @GetMapping
    public List<Map<String, Object>> list(@RequestParam(defaultValue = "30") int limit) {
        return backtestService.listRuns(Math.min(100, Math.max(1, limit)));
    }

    @GetMapping("/{id}")
    public Map<String, Object> get(@PathVariable long id) {
        return backtestService.getRun(id);
    }
}
