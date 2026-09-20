package ua.lz.ep.dto.payload;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class ProcessingResult {
    private List<String> lostEditions = new ArrayList<>();
    private LocalDateTime processingStartTime;
}
