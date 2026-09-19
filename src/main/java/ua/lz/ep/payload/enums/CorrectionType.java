package ua.lz.ep.payload.enums;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Correction mode used for Solr consistency checks")
public enum CorrectionType {
    @Schema(description = "Validate document identifiers against related editions")
    ONLY_DOCUMENT_ID,

    @Schema(description = "Validate concrete edition identifiers")
    EDITIONS_ID
}
