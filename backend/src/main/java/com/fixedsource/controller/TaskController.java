package com.fixedsource.controller;

import com.fixedsource.service.AuthService;
import com.fixedsource.service.TaskService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
public class TaskController {

    private final TaskService taskService;
    private final AuthService authService;

    public TaskController(TaskService taskService, AuthService authService) {
        this.taskService = taskService;
        this.authService = authService;
    }

    @GetMapping("/api/inspection-tasks")
    public List<Map<String, Object>> list(@RequestParam(required = false) String status,
                                          @RequestParam(required = false) String keyword) {
        return taskService.listTasks(status, keyword);
    }

    @GetMapping("/api/inspection-tasks/{id}")
    public Map<String, Object> detail(@PathVariable long id) {
        return taskService.getTask(id);
    }

    @PostMapping("/api/inspection-tasks")
    public Map<String, Object> create(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        AuthService.User user = (AuthService.User) request.getAttribute("user");
        require(user, "reviewer");
        Object clueId = body.get("clueId");
        if (clueId == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "缺少 clueId");
        return taskService.createFromClue(((Number) clueId).longValue(), user.name());
    }

    @PostMapping("/api/inspection-tasks/{id}/dispatch")
    public Map<String, Object> dispatch(@PathVariable long id, HttpServletRequest request) {
        AuthService.User user = (AuthService.User) request.getAttribute("user");
        require(user, "reviewer");
        return taskService.dispatch(id, user.name());
    }

    @PostMapping("/api/inspection-tasks/{id}/feedback")
    public Map<String, Object> feedback(@PathVariable long id, @RequestBody Map<String, Object> body,
                                        HttpServletRequest request) {
        AuthService.User user = (AuthService.User) request.getAttribute("user");
        require(user, "reviewer", "inspector");
        Map<String, Object> task = taskService.getTask(id);
        if (task == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "任务不存在");
        return taskService.feedback(String.valueOf(task.get("taskNo")), body, user.name());
    }

    @PostMapping("/api/inspection-tasks/{id}/close")
    public Map<String, Object> close(@PathVariable long id, HttpServletRequest request) {
        AuthService.User user = (AuthService.User) request.getAttribute("user");
        require(user, "reviewer");
        return taskService.close(id, user.name());
    }

    @PostMapping("/api/inspection-tasks/{id}/urge")
    public Map<String, Object> urge(@PathVariable long id, HttpServletRequest request) {
        AuthService.User user = (AuthService.User) request.getAttribute("user");
        require(user, "reviewer");
        return taskService.urge(id, user.name());
    }

    @GetMapping("/api/mobile/tasks")
    public List<Map<String, Object>> mobileTasks() {
        return taskService.mobileTasks();
    }

    @GetMapping("/api/mobile/tasks/{taskNo}")
    public Map<String, Object> mobileTask(@PathVariable String taskNo) {
        return taskService.getTaskByNo(taskNo);
    }

    @PostMapping("/api/mobile/tasks/{taskNo}/accept")
    public Map<String, Object> accept(@PathVariable String taskNo, HttpServletRequest request) {
        AuthService.User user = (AuthService.User) request.getAttribute("user");
        require(user, "inspector");
        return taskService.accept(taskNo, user.name());
    }

    @PostMapping("/api/mobile/tasks/{taskNo}/feedback")
    public Map<String, Object> mobileFeedback(@PathVariable String taskNo, @RequestBody Map<String, Object> body,
                                              HttpServletRequest request) {
        AuthService.User user = (AuthService.User) request.getAttribute("user");
        require(user, "inspector", "reviewer");
        return taskService.feedback(taskNo, body, user.name());
    }

    private void require(AuthService.User user, String... roles) {
        if (!authService.hasAnyRole(user, roles)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前角色无权执行此操作");
        }
    }
}
