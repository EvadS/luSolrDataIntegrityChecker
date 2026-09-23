package ua.lz.ep.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Schema(description = "Common request containing optional period boundaries and optional document IDs.")
public class PeriodDocsRequest {

    @Schema(description = "Lower bound of the modification period filter, inclusive. Optional when documentIds are provided.", example = "2026-01-09T00:00:00")
    private LocalDateTime startPeriod;

    @Schema(description = "Upper bound of the modification period filter, inclusive. Optional when documentIds are provided.", example = "2026-07-09T23:59:00")
    private LocalDateTime endPeriod;

    @ArraySchema(schema = @Schema(description = "Document identifier", example = "doc-12345"), arraySchema = @Schema(description = "Optional list of document IDs to restrict the search scope"))
    private List<String> documentIds = new ArrayList<>();
}
