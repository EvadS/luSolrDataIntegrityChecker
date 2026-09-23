package ua.lz.ep.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import ua.lz.ep.payload.enums.EditionDiffResultType;

@Data
public class EditionListDiff extends PeriodDocsRequest {

    @Schema(description = "Controls how differences are reported (implementation-specific)")
    private EditionDiffResultType diffResultType;
}
