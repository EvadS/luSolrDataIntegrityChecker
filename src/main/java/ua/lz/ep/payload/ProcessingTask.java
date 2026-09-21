package ua.lz.ep.payload;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;
import ua.lz.ep.payload.enums.TaskStatus;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
@Schema(description = "Internal representation of an asynchronous processing task")
public class ProcessingTask {
    @Schema(description = "Unique task identifier", example = "7d0f22f5-0f86-4a4e-b2a0-76c0c8b8043d")
    private String id;

    @Schema(description = "Current task state", example = "PENDING")
    private volatile TaskStatus status = TaskStatus.PENDING;

    @Schema(description = "Task progress in percent", example = "0")
    private volatile Integer progress = 0;

    @Schema(description = "Documents number", example = "0")
    private volatile Integer documentsNumber = 0;

    @Schema(description = "Number of documents processed", example = "0")
    private volatile Integer documentsProcessed = 0;

    @Schema(description = "Current task message", example = "Task created")
    private volatile String message = "Task created";

    @Schema(description = "Timestamp when the task was created", example = "2026-09-19T10:00:00")
    private volatile LocalDateTime createdAt = LocalDateTime.now();

    @Schema(description = "Timestamp when the task started running", example = "2026-09-19T10:00:01")
    private volatile LocalDateTime startedAt;

    @Schema(description = "Timestamp when the task finished", example = "2026-09-19T10:05:22")
    private volatile LocalDateTime completedAt;

    public synchronized void initialize(String taskId, String taskMessage) {
        this.id = taskId;
        this.createdAt = LocalDateTime.now();
        this.startedAt = null;
        this.completedAt = null;
        this.status = TaskStatus.PENDING;
        this.progress = 0;
        updateMessage(taskMessage);
    }

    public synchronized void markPending(String taskMessage) {
        if (status.isTerminal()) {
            return;
        }

        this.status = TaskStatus.PENDING;
        updateMessage(taskMessage);
    }

    public synchronized void markRunning(String taskMessage) {
        if (status.isTerminal()) {
            return;
        }

        this.status = TaskStatus.RUNNING;
        if (this.startedAt == null) {
            this.startedAt = LocalDateTime.now();
        }
        updateMessage(taskMessage);
    }

    public synchronized void updateProgress(int value) {
        updateProgress(value, null);
    }

    public synchronized void updateProgress(int value, String taskMessage) {
        if (status.isTerminal()) {
            return;
        }

        this.progress = Math.max(0, Math.min(100, value));
        updateMessage(taskMessage);
    }

    public synchronized void markCompleted(String taskMessage) {
        if (status.isTerminal()) {
            return;
        }

        this.status = TaskStatus.COMPLETED;
        this.progress = 100;
        this.completedAt = LocalDateTime.now();
        updateMessage(taskMessage);
    }

    public synchronized void markFailed(String taskMessage) {
        if (status.isTerminal()) {
            return;
        }

        this.status = TaskStatus.FAILED;
        this.completedAt = LocalDateTime.now();
        updateMessage(taskMessage);
    }

    public synchronized void markCancelled(String taskMessage) {
        if (status == TaskStatus.COMPLETED || status == TaskStatus.FAILED || status == TaskStatus.CANCELLED) {
            return;
        }

        this.status = TaskStatus.CANCELLED;
        this.completedAt = LocalDateTime.now();
        updateMessage(taskMessage);
    }

    public synchronized boolean isTerminal() {
        return status.isTerminal();
    }

    public synchronized void setDocumentsNumber(int documentsNumber) {
        this.documentsNumber = documentsNumber;
    }

    public synchronized void setDocumentsProcessed(int documentsProcessed) {
        this.documentsProcessed = documentsProcessed;
    }

    private void updateMessage(String taskMessage) {
        if (taskMessage != null && !taskMessage.isBlank()) {
            this.message = taskMessage;
        }
    }
}
