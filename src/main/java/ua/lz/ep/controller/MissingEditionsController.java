package ua.lz.ep.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import ua.lz.ep.dto.PeriodRequest;
import ua.lz.ep.dto.request.PageRequest;
import ua.lz.ep.dto.response.ApiErrorResponse;
import ua.lz.ep.dto.response.PageTaskStatus;
import ua.lz.ep.dto.response.TaskStatus;
import ua.lz.ep.dto.response.TaskSubmissionResponse;
import ua.lz.ep.payload.ProcessingTask;
import ua.lz.ep.service.MissingEditionManagerService;


@RestController
@RequestMapping("/api/corrections")
@Validated
public class MissingEditionsController implements MissingEditionsApi {

    private final MissingEditionManagerService managerService;

    public MissingEditionsController(MissingEditionManagerService managerService) {
        this.managerService = managerService;
    }

    @PostMapping(path = {"/missing-editions"})
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ResponseEntity<TaskSubmissionResponse> createMissingEditionsTask(
            @Valid @org.springframework.web.bind.annotation.RequestBody PeriodRequest periodRequest,
            HttpServletRequest request) {
        ProcessingTask processingTask = new ProcessingTask();
        String id = managerService.submitMissingEditionsTask(processingTask, periodRequest);
        String statusUrl = request.getContextPath() + "/api/corrections/missing-editions/tasks/" + id;
        TaskSubmissionResponse response = new TaskSubmissionResponse(id, "PENDING", statusUrl, statusUrl);

        return ResponseEntity.accepted()
                .header(HttpHeaders.LOCATION, statusUrl)
                .body(response);
    }

    @GetMapping("/missing-editions/tasks/{id}")
    public ResponseEntity<?> getTaskStatus(
            @PathVariable("id") String taskId,
            HttpServletRequest request) {
        TaskStatus taskStatus = managerService.getTaskStatus(taskId);
        if (taskStatus == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiErrorResponse.of(HttpStatus.NOT_FOUND, "Task '" + taskId + "' was not found.", request.getRequestURI()));
        }
        return ResponseEntity.ok(taskStatus);
    }

    @DeleteMapping("/missing-editions/tasks/{id}")
    public ResponseEntity<?> cancelTask(
            @PathVariable("id") String taskId,
            HttpServletRequest request) {
        TaskStatus currentStatus = managerService.getTaskStatus(taskId);
        if (currentStatus == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiErrorResponse.of(HttpStatus.NOT_FOUND, "Task '" + taskId + "' was not found.", request.getRequestURI()));
        }

        managerService.stopTask(taskId);
        return ResponseEntity.ok(managerService.getTaskStatus(taskId));
    }

    @GetMapping("/missing-editions/tasks")
    public ResponseEntity<PageTaskStatus<TaskStatus>> getTasks(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Positive int size,
            @RequestParam(required = false) String sort) {
        PageRequest pageable = new PageRequest();
        pageable.setPage(page);
        pageable.setSize(size);
        pageable.setSort(sort);

        return ResponseEntity.ok(managerService.findAll(pageable));
    }

}
