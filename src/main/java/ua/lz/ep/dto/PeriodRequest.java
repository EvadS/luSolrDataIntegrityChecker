package ua.lz.ep.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import ua.lz.ep.payload.enums.CorrectionType;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Schema(description = "Parameters for searching broken or missing editions in Solr")
public class PeriodRequest {

    @NotNull(message = "correctionType is required")
    @Schema(description = "Correction strategy that defines what is searched in Solr", example = "ONLY_DOCUMENT_ID")
    private CorrectionType correctionType;

    @Schema(description = "Lower bound of the modification period filter, inclusive. Optional when documentIds are provided.", example = "2026-01-09T00:00:00")
    private LocalDateTime startPeriod;

    @Schema(description = "Upper bound of the modification period filter, inclusive. Optional when documentIds are provided.", example = "2026-07-09T23:59:00")
    private LocalDateTime endPeriod;

    @ArraySchema(schema = @Schema(description = "Document identifier", example = "doc-12345"), arraySchema = @Schema(description = "Optional list of document IDs to restrict the search scope"))
    private List<String> documentIds = new ArrayList<>();
}
