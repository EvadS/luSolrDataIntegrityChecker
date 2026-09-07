package ua.lz.ep.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ua.lz.ep.service.TaskManagerService;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private final TaskManagerService taskService;

    public TaskController(TaskManagerService taskService) {
        this.taskService = taskService;
    }

    // Endpoint to start a custom task with a specific ID
    @PostMapping("/start/{taskId}")
    public ResponseEntity<String> startTask(@PathVariable String taskId) {
        taskService.startTask(taskId, new ua.lz.ep.payload.SampleTask());
        return ResponseEntity.ok("Task '" + taskId + "' started successfully.");
    }

    // Endpoint to manually stop/cancel a running task by ID
    @PostMapping("/stop/{taskId}")
    public ResponseEntity<String> stopTask(@PathVariable String taskId) {
        boolean stopped = taskService.stopTask(taskId);
        if (stopped) {
            return ResponseEntity.ok("Task '" + taskId + "' was manually stopped.");
        } else {
            return ResponseEntity.badRequest().body("Task '" + taskId + "' could not be found or stopped.");
        }
    }
}