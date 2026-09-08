package ua.lz.ep.payload;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ProcessingTask {
    private Long id;

    private String status;

    private Integer progress;
    private String message;

    private LocalDateTime startedAt = LocalDateTime.now();
    private LocalDateTime completedAt;
}
