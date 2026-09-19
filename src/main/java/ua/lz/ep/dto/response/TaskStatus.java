package ua.lz.ep.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Current status of an asynchronous correction task")
public class TaskStatus {
    @Schema(description = "Unique task identifier", example = "7d0f22f5-0f86-4a4e-b2a0-76c0c8b8043d", requiredMode = Schema.RequiredMode.REQUIRED)
    private String taskId;

    @Schema(description = "Task lifecycle state", example = "RUNNING", allowableValues = {"PENDING", "RUNNING", "CANCELLED", "TIMED_OUT", "COMPLETED", "FAILED"}, requiredMode = Schema.RequiredMode.REQUIRED)
    private String status;

    @Schema(description = "Execution progress in percent", example = "45", minimum = "0", maximum = "100", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer progress;

    @Schema(description = "Additional execution details or error message", example = "Task is running", requiredMode = Schema.RequiredMode.REQUIRED)
    private String message;

    @Schema(description = "Task creation timestamp", example = "2026-09-19T10:00:00")
    private LocalDateTime createdAt;

    @Schema(description = "Task start timestamp", example = "2026-09-19T10:00:01")
    private LocalDateTime startedAt;

    @Schema(description = "Task completion timestamp", example = "2026-09-19T10:05:22")
    private LocalDateTime completedAt;

    @Schema(description = "Whether the task is still active in the executor", example = "true")
    private boolean active;
}
