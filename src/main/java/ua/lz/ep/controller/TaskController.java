package ua.lz.ep.controller;

import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ua.lz.ep.dto.response.ApiErrorResponse;
import ua.lz.ep.service.TaskManagerService;

@RestController
@RequestMapping("/api/tasks")
@Tag(name = "Technical tasks", description = "Auxiliary endpoints for manually starting and stopping demo background tasks")
@Hidden
public class TaskController {

    private final TaskManagerService taskService;

    public TaskController(TaskManagerService taskService) {
        this.taskService = taskService;
    }

    // Endpoint to start a custom task with a specific ID
    @PostMapping("/start/{taskId}")
    @Operation(summary = "Start demo task", description = "Starts a sample background task with a custom identifier.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Task started successfully",
                    content = @Content(schema = @Schema(implementation = String.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<String> startTask(@PathVariable String taskId) {
        taskService.startTask(taskId, new ua.lz.ep.payload.SampleTask());
        return ResponseEntity.ok("Task '" + taskId + "' started successfully.");
    }

    // Endpoint to manually stop/cancel a running task by ID
    @PostMapping("/stop/{taskId}")
    @Operation(summary = "Stop demo task", description = "Stops a sample background task by its identifier.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Task stopped successfully",
                    content = @Content(schema = @Schema(implementation = String.class))),
            @ApiResponse(responseCode = "400", description = "Task could not be found or stopped",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<?> stopTask(@PathVariable String taskId, HttpServletRequest request) {
        boolean stopped = taskService.stopTask(taskId);
        if (stopped) {
            return ResponseEntity.ok("Task '" + taskId + "' was manually stopped.");
        } else {
            return ResponseEntity.badRequest()
                    .body(ApiErrorResponse.of(HttpStatus.BAD_REQUEST,
                            "Task '" + taskId + "' could not be found or stopped.",
                            request.getRequestURI()));
        }
    }
}