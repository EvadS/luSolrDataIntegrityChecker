package ua.lz.ep.controller;

import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ua.lz.ep.dto.PeriodRequest;
import ua.lz.ep.dto.request.PageRequest;
import ua.lz.ep.dto.response.ApiErrorResponse;
import ua.lz.ep.dto.response.PageTaskStatus;
import ua.lz.ep.dto.response.TaskStatus;
import ua.lz.ep.payload.ProcessingTask;
import ua.lz.ep.service.EditionDocsManagerService;

@Hidden
@RestController
@RequestMapping("/api/correction/task")
public class LegacyCorrectionController implements LegacyCorrectionApi {

    private final EditionDocsManagerService managerService;

    public LegacyCorrectionController(EditionDocsManagerService managerService) {
        this.managerService = managerService;
    }

    @PostMapping("/start")
    public ResponseEntity<String> startProcessing(@Valid @RequestBody PeriodRequest periodRequest) {
        ProcessingTask processingTask = new ProcessingTask();
        String id = managerService.submitMissingEditionsTask(processingTask, periodRequest);
        return ResponseEntity.accepted().body(id);
    }

    @GetMapping("/status/{id}")
    public ResponseEntity<?> getTaskStatus(@PathVariable("id") String taskId, HttpServletRequest request) {
        TaskStatus taskStatus = managerService.getTaskStatus(taskId);
        if (taskStatus == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiErrorResponse.of(HttpStatus.NOT_FOUND, "Task '" + taskId + "' was not found.", request.getRequestURI()));
        }
        return ResponseEntity.ok(taskStatus);
    }

    @PostMapping("/stop/{id}")
    public ResponseEntity<?> stopTask(@PathVariable("id") String taskId, HttpServletRequest request) {
        TaskStatus currentStatus = managerService.getTaskStatus(taskId);
        if (currentStatus == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiErrorResponse.of(HttpStatus.NOT_FOUND, "Task '" + taskId + "' was not found.", request.getRequestURI()));
        }

        managerService.stopTask(taskId);
        return ResponseEntity.ok(managerService.getTaskStatus(taskId));
    }

    @PostMapping("/list")
    public ResponseEntity<PageTaskStatus<TaskStatus>> getTasks(@RequestBody(required = false) PageRequest pageable) {
        return ResponseEntity.ok(managerService.findAll(pageable));
    }
}

