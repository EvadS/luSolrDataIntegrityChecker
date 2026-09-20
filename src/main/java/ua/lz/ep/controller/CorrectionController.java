package ua.lz.ep.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import ua.lz.ep.config.OpenApiConstants;
import ua.lz.ep.dto.PeriodRequest;
import ua.lz.ep.dto.request.PageRequest;
import ua.lz.ep.dto.response.ApiErrorResponse;
import ua.lz.ep.dto.response.PageTaskStatus;
import ua.lz.ep.dto.response.TaskStatus;
import ua.lz.ep.dto.response.TaskSubmissionResponse;
import ua.lz.ep.payload.ProcessingTask;
import ua.lz.ep.service.TaskManagerService;


@RestController
@RequestMapping("/api/correction/tasks")
@Tag(name = "Correction tasks",
        description = "Operations for starting, tracking, listing and stopping asynchronous Solr correction tasks")
@Validated
public class CorrectionController {

    private final TaskManagerService managerService;

    public CorrectionController(TaskManagerService managerService) {
        this.managerService = managerService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Create correction task", description = "Creates an asynchronous correction task and immediately returns its identifier and resource links.")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Task accepted for asynchronous processing",
                    content = @Content(schema = @Schema(implementation = TaskSubmissionResponse.class))),
            @ApiResponse(ref = OpenApiConstants.BAD_REQUEST_RESPONSE_REF),
            @ApiResponse(ref = OpenApiConstants.INTERNAL_SERVER_ERROR_RESPONSE_REF)
    })
    public ResponseEntity<TaskSubmissionResponse> createTask(
            @RequestBody(description = "Correction request with optional documentIds filter and optional period boundaries.", required = true,
                    content = @Content(schema = @Schema(implementation = PeriodRequest.class), examples = {
                            @ExampleObject(name = "By period", value = """
                                    {
                                      "correctionType": "ONLY_DOCUMENT_ID",
                                      "startPeriod": "2026-01-09T00:00:00",
                                      "endPeriod": "2026-07-09T23:59:00",
                                      "documentIds": []
                                    }
                                    """),
                            @ExampleObject(name = "By documents", value = """
                                    {
                                      "correctionType": "EDITIONS_ID",
                                      "documentIds": ["doc-1001", "doc-1002"]
                                    }
                                    """)
                    }))
            @Valid @org.springframework.web.bind.annotation.RequestBody PeriodRequest periodRequest,
            HttpServletRequest request) {
        ProcessingTask processingTask = new ProcessingTask();
        String id = managerService.processCorrection(processingTask, periodRequest);
        String statusUrl = request.getContextPath() + "/api/correction/tasks/" + id;
        TaskSubmissionResponse response = new TaskSubmissionResponse(id, "PENDING", statusUrl, statusUrl);

        return ResponseEntity.accepted()
                .header(HttpHeaders.LOCATION, statusUrl)
                .body(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get task status", description = "Returns the current state and progress of an asynchronous correction task.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Task status found",
                    content = @Content(schema = @Schema(implementation = TaskStatus.class))),
            @ApiResponse(ref = OpenApiConstants.NOT_FOUND_RESPONSE_REF),
            @ApiResponse(ref = OpenApiConstants.INTERNAL_SERVER_ERROR_RESPONSE_REF)
    })
    public ResponseEntity<?> getTaskStatus(
            @Parameter(description = "Identifier returned by the start endpoint", example = "7d0f22f5-0f86-4a4e-b2a0-76c0c8b8043d")
            @PathVariable("id") String taskId,
            HttpServletRequest request) {
        TaskStatus taskStatus = managerService.getTaskStatus(taskId);
        if (taskStatus == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiErrorResponse.of(HttpStatus.NOT_FOUND, "Task '" + taskId + "' was not found.", request.getRequestURI()));
        }
        return ResponseEntity.ok(taskStatus);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Cancel task", description = "Requests cancellation of an asynchronous correction task and returns the updated status.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Task cancellation requested",
                    content = @Content(schema = @Schema(implementation = TaskStatus.class))),
            @ApiResponse(ref = OpenApiConstants.NOT_FOUND_RESPONSE_REF),
            @ApiResponse(ref = OpenApiConstants.INTERNAL_SERVER_ERROR_RESPONSE_REF)
    })
    public ResponseEntity<?> cancelTask(
            @Parameter(description = "Identifier returned by the start endpoint", example = "7d0f22f5-0f86-4a4e-b2a0-76c0c8b8043d")
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

    @GetMapping
    @Operation(summary = "List tasks", description = "Returns a paged list of known correction tasks stored in memory.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Task list returned",
                    content = @Content(schema = @Schema(implementation = PageTaskStatus.class))),
            @ApiResponse(ref = OpenApiConstants.BAD_REQUEST_RESPONSE_REF),
            @ApiResponse(ref = OpenApiConstants.INTERNAL_SERVER_ERROR_RESPONSE_REF)
    })
    public ResponseEntity<PageTaskStatus<TaskStatus>> getTasks(
            @Parameter(description = "Zero-based page index", example = "0")
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @Parameter(description = "Page size", example = "20")
            @RequestParam(defaultValue = "20") @Positive int size,
            @Parameter(description = "Optional sort expression. Supported values: createdAt, startedAt, completedAt, status, progress, taskId. Use ',desc' for descending order.", example = "createdAt,desc")
            @RequestParam(required = false) String sort) {
        PageRequest pageable = new PageRequest();
        pageable.setPage(page);
        pageable.setSize(size);
        pageable.setSort(sort);

        return ResponseEntity.ok(managerService.findAll(pageable));
    }

}
