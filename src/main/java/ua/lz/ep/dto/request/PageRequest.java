package ua.lz.ep.dto.request;


import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
@Schema(description = "Pagination request for browsing task statuses")
public class PageRequest {

    @Min(value = 0, message = "page must be greater than or equal to 0")
    @Schema(description = "Zero-based page index", example = "0", defaultValue = "0")
    private int page;

    @Positive(message = "size must be greater than 0")
    @Schema(description = "Page size", example = "20", defaultValue = "20")
    private int size;

    @Schema(description = "Optional sort expression", example = "createdAt,desc")
    private String sort;

}
