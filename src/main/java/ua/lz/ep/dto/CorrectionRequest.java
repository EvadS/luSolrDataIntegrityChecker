package ua.lz.ep.dto;

import lombok.Data;
import ua.lz.ep.payload.enums.CorrectionType;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class CorrectionRequest {
    private CorrectionType correctionType;
    private LocalDateTime startPeriod;
    private LocalDateTime endPeriod;
}
