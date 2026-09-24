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
import ua.lz.ep.config.OpenApiConstants;
import ua.lz.ep.dto.PeriodDocsRequest;
import ua.lz.ep.dto.PeriodRequest;
import ua.lz.ep.dto.EditionListDiff;
import ua.lz.ep.dto.response.PageTaskStatus;
import ua.lz.ep.dto.response.TaskStatus;

import ua.lz.ep.dto.response.TaskSubmissionResponse;

@Tag(name = "Corrections",
        description = "Operations for starting, tracking, listing and stopping correction tasks (missing editions, edition counts)")
public interface EditionDocsControllerApi {

    @Operation(summary = "Start missing editions check",
            description = "Starts an asynchronous task that detects missing editions according to the provided filter (period or explicit documentIds). Returns task identifier and links to status/report.")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Task accepted for asynchronous processing",
                    content = @Content(schema = @Schema(implementation = TaskSubmissionResponse.class))),
            @ApiResponse(ref = OpenApiConstants.BAD_REQUEST_RESPONSE_REF),
            @ApiResponse(ref = OpenApiConstants.INTERNAL_SERVER_ERROR_RESPONSE_REF)
    })
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
    org.springframework.http.ResponseEntity<TaskSubmissionResponse> createMissingEditionsTask(@Valid PeriodRequest periodRequest, HttpServletRequest request);

    @Operation(summary = "Start invalid first date check",
            description = "Starts an asynchronous task that detects documents with an invalid first edition date according to the provided filter (period or explicit documentIds). Returns task identifier and links to status/report.")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Task accepted for asynchronous processing",
                    content = @Content(schema = @Schema(implementation = TaskSubmissionResponse.class))),
            @ApiResponse(ref = OpenApiConstants.BAD_REQUEST_RESPONSE_REF),
            @ApiResponse(ref = OpenApiConstants.INTERNAL_SERVER_ERROR_RESPONSE_REF)
    })
    @RequestBody(description = "Request with optional documentIds filter and optional period boundaries. Do not set correctionType.", required = true,
            content = @Content(schema = @Schema(implementation = PeriodDocsRequest.class), examples = {
                    @ExampleObject(name = "By period", value = """
                            {
                              "startPeriod": "2026-01-09T00:00:00",
                              "endPeriod": "2026-07-09T23:59:00",
                              "documentIds": []
                            }
                            """),
                    @ExampleObject(name = "By documents", value = """
                            {
                              "documentIds": ["doc-1001", "doc-1002"]
                            }
                            """)
            }))
    org.springframework.http.ResponseEntity<TaskSubmissionResponse> createInvalidFirstDateTask(@Valid PeriodDocsRequest periodRequest, HttpServletRequest request);

    @Operation(summary = "Get invalid-first-date task status", description = "Returns the current state and progress of an invalid-first-date asynchronous task.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Task status found",
                    content = @Content(schema = @Schema(implementation = TaskStatus.class)) ),
            @ApiResponse(ref = OpenApiConstants.NOT_FOUND_RESPONSE_REF),
            @ApiResponse(ref = OpenApiConstants.INTERNAL_SERVER_ERROR_RESPONSE_REF)
    })
    org.springframework.http.ResponseEntity<?> getInvalidFirstDateTaskStatus(@Parameter(description = "Identifier returned by the start endpoint", example = "7d0f22f5-0f86-4a4e-b2a0-76c0c8b8043d") String taskId, HttpServletRequest request);

    @Operation(summary = "Cancel invalid-first-date task", description = "Requests cancellation of an invalid-first-date asynchronous task and returns the updated status.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Task cancellation requested",
                    content = @Content(schema = @Schema(implementation = TaskStatus.class)) ),
            @ApiResponse(ref = OpenApiConstants.NOT_FOUND_RESPONSE_REF),
            @ApiResponse(ref = OpenApiConstants.INTERNAL_SERVER_ERROR_RESPONSE_REF)
    })
    org.springframework.http.ResponseEntity<?> cancelInvalidFirstDateTask(@Parameter(description = "Identifier returned by the start endpoint", example = "7d0f22f5-0f86-4a4e-b2a0-76c0c8b8043d") String taskId, HttpServletRequest request);

    @Operation(summary = "List invalid-first-date tasks", description = "Returns a paged list of known invalid-first-date tasks stored in memory.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Task list returned",
                    content = @Content(schema = @Schema(implementation = PageTaskStatus.class)) ),
            @ApiResponse(ref = OpenApiConstants.BAD_REQUEST_RESPONSE_REF),
            @ApiResponse(ref = OpenApiConstants.INTERNAL_SERVER_ERROR_RESPONSE_REF)
    })
    org.springframework.http.ResponseEntity<PageTaskStatus<TaskStatus>> getInvalidFirstDateTasks(@Min(0) int page, @Positive int size, String sort);


    @Operation(summary = "Start editions diff check",
            description = "Starts an asynchronous task that checks edition id and edition_list_full.")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Task accepted for asynchronous processing",
                    content = @Content(schema = @Schema(implementation = TaskSubmissionResponse.class)) ),
            @ApiResponse(ref = OpenApiConstants.BAD_REQUEST_RESPONSE_REF),
            @ApiResponse(ref = OpenApiConstants.INTERNAL_SERVER_ERROR_RESPONSE_REF)
    })
    @RequestBody(description = "Request with optional documents filter and/or period boundaries. Do not set correctionType.", required = true,
            content = @Content(schema = @Schema(implementation = EditionListDiff.class), examples = {
                    @ExampleObject(name = "By period", value = """
                            {
                              "startPeriod": "2026-01-09T00:00:00",
                              "endPeriod": "2026-07-09T23:59:00",
                                                  "documentIds": []
                            }
                            """),
                    @ExampleObject(name = "By documents", value = """
                            {
                                                  "documentIds": ["doc-1001", "doc-1002"]
                            }
                            """)
            }))
    org.springframework.http.ResponseEntity<TaskSubmissionResponse> createEditionsDiffTask(@Valid EditionListDiff editionListDiff, HttpServletRequest request);


    @Operation(summary = "Get editions-diff task status", description = "Returns the current state and progress of an editions-diff asynchronous task.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Task status found",
                    content = @Content(schema = @Schema(implementation = TaskStatus.class)) ),
            @ApiResponse(ref = OpenApiConstants.NOT_FOUND_RESPONSE_REF),
            @ApiResponse(ref = OpenApiConstants.INTERNAL_SERVER_ERROR_RESPONSE_REF)
    })
    org.springframework.http.ResponseEntity<?> getEditionsDiffTaskStatus(@Parameter(description = "Identifier returned by the start endpoint", example = "7d0f22f5-0f86-4a4e-b2a0-76c0c8b8043d") String taskId, HttpServletRequest request);


    @Operation(summary = "Cancel editions-diff task", description = "Requests cancellation of an editions-diff asynchronous task and returns the updated status.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Task cancellation requested",
                    content = @Content(schema = @Schema(implementation = TaskStatus.class)) ),
            @ApiResponse(ref = OpenApiConstants.NOT_FOUND_RESPONSE_REF),
            @ApiResponse(ref = OpenApiConstants.INTERNAL_SERVER_ERROR_RESPONSE_REF)
    })
    org.springframework.http.ResponseEntity<?> cancelEditionsDiffTask(@Parameter(description = "Identifier returned by the start endpoint", example = "7d0f22f5-0f86-4a4e-b2a0-76c0c8b8043d") String taskId, HttpServletRequest request);


    @Operation(summary = "List editions-diff tasks", description = "Returns a paged list of known editions-diff tasks stored in memory.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Task list returned",
                    content = @Content(schema = @Schema(implementation = PageTaskStatus.class)) ),
            @ApiResponse(ref = OpenApiConstants.BAD_REQUEST_RESPONSE_REF),
            @ApiResponse(ref = OpenApiConstants.INTERNAL_SERVER_ERROR_RESPONSE_REF)
    })
    org.springframework.http.ResponseEntity<PageTaskStatus<TaskStatus>> getEditionsDiffTasks(@Min(0) int page, @Positive int size, String sort);


    @Operation(summary = "Get task status", description = "Returns the current state and progress of an asynchronous correction task.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Task status found",
                    content = @Content(schema = @Schema(implementation = TaskStatus.class))),
            @ApiResponse(ref = OpenApiConstants.NOT_FOUND_RESPONSE_REF),
            @ApiResponse(ref = OpenApiConstants.INTERNAL_SERVER_ERROR_RESPONSE_REF)
    })
    org.springframework.http.ResponseEntity<?> getTaskStatus(@Parameter(description = "Identifier returned by the start endpoint", example = "7d0f22f5-0f86-4a4e-b2a0-76c0c8b8043d") String taskId, HttpServletRequest request);


    @Operation(summary = "Cancel task", description = "Requests cancellation of an asynchronous correction task and returns the updated status.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Task cancellation requested",
                    content = @Content(schema = @Schema(implementation = TaskStatus.class))),
            @ApiResponse(ref = OpenApiConstants.NOT_FOUND_RESPONSE_REF),
            @ApiResponse(ref = OpenApiConstants.INTERNAL_SERVER_ERROR_RESPONSE_REF)
    })
    org.springframework.http.ResponseEntity<?> cancelTask(@Parameter(description = "Identifier returned by the start endpoint", example = "7d0f22f5-0f86-4a4e-b2a0-76c0c8b8043d") String taskId, HttpServletRequest request);


    @Operation(summary = "List tasks", description = "Returns a paged list of known correction tasks stored in memory.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Task list returned",
                    content = @Content(schema = @Schema(implementation = PageTaskStatus.class))),
            @ApiResponse(ref = OpenApiConstants.BAD_REQUEST_RESPONSE_REF),
            @ApiResponse(ref = OpenApiConstants.INTERNAL_SERVER_ERROR_RESPONSE_REF)
    })
    org.springframework.http.ResponseEntity<PageTaskStatus<TaskStatus>> getTasks(@Min(0) int page, @Positive int size, String sort);

}
