package com.fixedsource.controller;

import com.fixedsource.service.AuthService;
import com.fixedsource.service.EventRiskService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/events")
public class EventController {

    private final EventRiskService eventRiskService;
    private final AuthService authService;

    public EventController(EventRiskService eventRiskService, AuthService authService) {
        this.eventRiskService = eventRiskService;
        this.authService = authService;
    }

    @GetMapping
    public List<Map<String, Object>> list(@RequestParam(required = false) String status,
                                          @RequestParam(required = false) String priority,
                                          @RequestParam(required = false) String enterpriseId,
                                          @RequestParam(required = false) String keyword) {
        return eventRiskService.listEvents(status, priority, enterpriseId, keyword);
    }

    @GetMapping("/{id}")
    public Map<String, Object> detail(@PathVariable long id) {
        return eventRiskService.getEvent(id);
    }

    @PostMapping("/{id}/split")
    public Map<String, Object> split(@PathVariable long id, @RequestBody Map<String, Object> body,
                                     HttpServletRequest request) {
        AuthService.User user = (AuthService.User) request.getAttribute("user");
        if (!authService.hasAnyRole(user, "reviewer")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只有审核人员可以拆分事件");
        }
        List<Long> clueIds = new ArrayList<>();
        Object raw = body.get("clueIds");
        if (raw instanceof List<?> list) {
            for (Object item : list) clueIds.add(((Number) item).longValue());
        }
        return eventRiskService.splitEvent(id, clueIds, user.name());
    }

    @PostMapping("/merge")
    public Map<String, Object> merge(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        AuthService.User user = (AuthService.User) request.getAttribute("user");
        if (!authService.hasAnyRole(user, "reviewer")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只有审核人员可以合并事件");
        }
        List<Long> ids = new ArrayList<>();
        Object raw = body.get("eventIds");
        if (raw instanceof List<?> list) {
            for (Object item : list) ids.add(((Number) item).longValue());
        }
        return eventRiskService.mergeEvents(ids, user.name());
    }
}
