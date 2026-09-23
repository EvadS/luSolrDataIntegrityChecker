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
public class PeriodRequest extends PeriodDocsRequest {

    @NotNull(message = "correctionType is required")
    @Schema(description = "Correction strategy that defines what is searched in Solr", example = "ONLY_DOCUMENT_ID")
    private CorrectionType correctionType;
}
