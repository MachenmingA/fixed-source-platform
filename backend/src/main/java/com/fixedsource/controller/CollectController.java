package com.fixedsource.controller;

import com.fixedsource.service.AuthService;
import com.fixedsource.service.CollectService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
public class CollectController {

    private final CollectService collectService;

    public CollectController(CollectService collectService) {
        this.collectService = collectService;
    }

    @GetMapping("/api/collect-batches")
    public List<Map<String, Object>> batches(@RequestParam(defaultValue = "30") int limit) {
        return collectService.listBatches(Math.min(100, Math.max(1, limit)));
    }

    @GetMapping("/api/collect-batches/{id}")
    public Map<String, Object> batch(@PathVariable long id) {
        return collectService.getBatch(id);
    }

    @PostMapping("/api/collect-batches")
    public Map<String, Object> collect(@RequestBody(required = false) Map<String, Object> body, HttpServletRequest request) {
        AuthService.User user = (AuthService.User) request.getAttribute("user");
        String dateText = body == null ? null : String.valueOf(body.get("bizDate"));
        LocalDate date = dateText == null || dateText.isBlank() || "null".equals(dateText)
                ? LocalDate.now() : LocalDate.parse(dateText);
        return collectService.collect(date, user.name());
    }
}
