package ua.lz.ep.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Unified error response returned by the API")
public class ApiErrorResponse {

    @Schema(description = "HTTP status code", example = "400", requiredMode = Schema.RequiredMode.REQUIRED)
    private int status;

    @Schema(description = "HTTP status reason", example = "Bad Request", requiredMode = Schema.RequiredMode.REQUIRED)
    private String error;

    @Schema(description = "Human-readable error message", example = "correctionType is required", requiredMode = Schema.RequiredMode.REQUIRED)
    private String message;

    @Schema(description = "Request path that produced the error", example = "/api/correction/tasks", requiredMode = Schema.RequiredMode.REQUIRED)
    private String path;

    @Schema(description = "Timestamp when the error occurred", example = "2026-09-19T10:15:30", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime timestamp;

    public static ApiErrorResponse of(HttpStatus status, String message, String path) {
        return new ApiErrorResponse(
                status.value(),
                status.getReasonPhrase(),
                message,
                path,
                LocalDateTime.now());
    }
}

