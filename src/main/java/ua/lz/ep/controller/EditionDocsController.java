package ua.lz.ep.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import ua.lz.ep.config.ApplicationConstants;
import ua.lz.ep.dto.PeriodDocsRequest;
import ua.lz.ep.dto.PeriodRequest;
import ua.lz.ep.dto.EditionListDiff;
import ua.lz.ep.dto.request.PageRequest;
import ua.lz.ep.dto.response.ApiErrorResponse;
import ua.lz.ep.dto.response.PageTaskStatus;
import ua.lz.ep.dto.response.TaskStatus;
import ua.lz.ep.dto.response.TaskSubmissionResponse;
import ua.lz.ep.payload.ProcessingTask;
import ua.lz.ep.service.EditionDocsManagerService;


@RestController
@RequestMapping(ApplicationConstants.DOCUMENTS_API)
@Validated
public class EditionDocsController implements EditionDocsControllerApi {


    private final EditionDocsManagerService managerService;

    public EditionDocsController(EditionDocsManagerService managerService) {
        this.managerService = managerService;
    }

    @PostMapping(path = {ApplicationConstants.MISSING_EDITIONS})
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ResponseEntity<TaskSubmissionResponse> createMissingEditionsTask(
            @Valid @org.springframework.web.bind.annotation.RequestBody PeriodRequest periodRequest,
            HttpServletRequest request) {
        ProcessingTask processingTask = new ProcessingTask();

        String id = managerService.submitMissingEditionsTask(processingTask, periodRequest);
        String statusUrl = request.getContextPath() + ApplicationConstants.DOCUMENTS_API + ApplicationConstants.MISSING_EDITIONS + "/tasks/" + id;
        TaskSubmissionResponse response = new TaskSubmissionResponse(id, "PENDING", statusUrl, statusUrl);

        return ResponseEntity.accepted()
                .header(HttpHeaders.LOCATION, statusUrl)
                .body(response);
    }

    @PostMapping(path = {ApplicationConstants.INVALID_FIRST_DATE})
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ResponseEntity<TaskSubmissionResponse> createInvalidFirstDateTask(
            @Valid @org.springframework.web.bind.annotation.RequestBody PeriodDocsRequest periodRequest,
            HttpServletRequest request) {
        ProcessingTask processingTask = new ProcessingTask();

        String id = managerService.submitInvalidFirstDateTask(processingTask, periodRequest);
        String statusUrl = request.getContextPath() + ApplicationConstants.DOCUMENTS_API + ApplicationConstants.INVALID_FIRST_DATE + "/tasks/" + id;
        TaskSubmissionResponse response = new TaskSubmissionResponse(id, "PENDING", statusUrl, statusUrl);

        return ResponseEntity.accepted()
                .header(HttpHeaders.LOCATION, statusUrl)
                .body(response);
    }

    @GetMapping(ApplicationConstants.INVALID_FIRST_DATE + "/tasks/{id}")
    public ResponseEntity<?> getInvalidFirstDateTaskStatus(
            @PathVariable("id") String taskId,
            HttpServletRequest request) {
        TaskStatus taskStatus = managerService.getTaskStatus(taskId);
        if (taskStatus == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiErrorResponse.of(HttpStatus.NOT_FOUND, "Task '" + taskId + "' was not found.", request.getRequestURI()));
        }
        return ResponseEntity.ok(taskStatus);
    }

    @DeleteMapping(ApplicationConstants.INVALID_FIRST_DATE + "/tasks/{id}")
    public ResponseEntity<?> cancelInvalidFirstDateTask(
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

    @GetMapping(ApplicationConstants.INVALID_FIRST_DATE + "/tasks")
    public ResponseEntity<PageTaskStatus<TaskStatus>> getInvalidFirstDateTasks(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort) {
        PageRequest pageable = new PageRequest();
        pageable.setPage(page);
        pageable.setSize(size);
        pageable.setSort(sort);

        return ResponseEntity.ok(managerService.findAll(pageable));
    }

    @GetMapping(ApplicationConstants.MISSING_EDITIONS + "/tasks/{id}")
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

    @DeleteMapping(ApplicationConstants.MISSING_EDITIONS + "/tasks/{id}")
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

    @GetMapping(ApplicationConstants.MISSING_EDITIONS + "/tasks")
    public ResponseEntity<PageTaskStatus<TaskStatus>> getTasks(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort) {
        PageRequest pageable = new PageRequest();
        pageable.setPage(page);
        pageable.setSize(size);
        pageable.setSort(sort);

        return ResponseEntity.ok(managerService.findAll(pageable));
    }

    // ------------------ editions diff endpoints ------------------
    @PostMapping(path = {ApplicationConstants.EDITIONS_DIFF})
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ResponseEntity<TaskSubmissionResponse> createEditionsDiffTask(
            @Valid @org.springframework.web.bind.annotation.RequestBody EditionListDiff editionListDiff,
            HttpServletRequest request) {
        ProcessingTask processingTask = new ProcessingTask();

        String id = managerService.submitEditionsDiffTask(processingTask, editionListDiff);
        String statusUrl = request.getContextPath() + ApplicationConstants.DOCUMENTS_API + ApplicationConstants.EDITIONS_DIFF + "/tasks/" + id;
        TaskSubmissionResponse response = new TaskSubmissionResponse(id, "PENDING", statusUrl, statusUrl);

        return ResponseEntity.accepted()
                .header(HttpHeaders.LOCATION, statusUrl)
                .body(response);
    }

    @GetMapping(ApplicationConstants.EDITIONS_DIFF + "/tasks/{id}")
    public ResponseEntity<?> getEditionsDiffTaskStatus(
            @PathVariable("id") String taskId,
            HttpServletRequest request) {
        TaskStatus taskStatus = managerService.getTaskStatus(taskId);
        if (taskStatus == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiErrorResponse.of(HttpStatus.NOT_FOUND, "Task '" + taskId + "' was not found.", request.getRequestURI()));
        }
        return ResponseEntity.ok(taskStatus);
    }

    @DeleteMapping(ApplicationConstants.EDITIONS_DIFF + "/tasks/{id}")
    public ResponseEntity<?> cancelEditionsDiffTask(
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

    @GetMapping(ApplicationConstants.EDITIONS_DIFF + "/tasks")
    public ResponseEntity<PageTaskStatus<TaskStatus>> getEditionsDiffTasks(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort) {
        PageRequest pageable = new PageRequest();
        pageable.setPage(page);
        pageable.setSize(size);
        pageable.setSort(sort);

        return ResponseEntity.ok(managerService.findAll(pageable));
    }

}
