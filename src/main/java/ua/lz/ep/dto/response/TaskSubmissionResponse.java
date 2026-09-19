package ua.lz.ep.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Schema(description = "Response returned when an asynchronous correction task is accepted")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TaskSubmissionResponse {

    @Schema(description = "Unique task identifier", example = "7d0f22f5-0f86-4a4e-b2a0-76c0c8b8043d", requiredMode = Schema.RequiredMode.REQUIRED)
    private String taskId;

    @Schema(description = "Initial task state", example = "PENDING", requiredMode = Schema.RequiredMode.REQUIRED)
    private String status;

    @Schema(description = "Relative URI to fetch the task status", example = "/api/correction/tasks/7d0f22f5-0f86-4a4e-b2a0-76c0c8b8043d", requiredMode = Schema.RequiredMode.REQUIRED)
    private String statusUrl;

    @Schema(description = "Relative URI to cancel the task", example = "/api/correction/tasks/7d0f22f5-0f86-4a4e-b2a0-76c0c8b8043d", requiredMode = Schema.RequiredMode.REQUIRED)
    private String cancelUrl;
}

