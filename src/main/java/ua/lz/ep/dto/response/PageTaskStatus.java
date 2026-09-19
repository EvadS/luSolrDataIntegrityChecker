package ua.lz.ep.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Paged result wrapper for task statuses")
public class PageTaskStatus<T> {
	@Schema(description = "Current page content", requiredMode = Schema.RequiredMode.REQUIRED)
	private List<T> content;

	@Schema(description = "Zero-based page index", example = "0", minimum = "0", requiredMode = Schema.RequiredMode.REQUIRED)
	private int page;

	@Schema(description = "Requested page size", example = "20", minimum = "1", requiredMode = Schema.RequiredMode.REQUIRED)
	private int size;

	@Schema(description = "Total number of elements", example = "3", minimum = "0", requiredMode = Schema.RequiredMode.REQUIRED)
	private long totalElements;

	@Schema(description = "Total number of available pages", example = "1", minimum = "0", requiredMode = Schema.RequiredMode.REQUIRED)
	private int totalPages;
}
