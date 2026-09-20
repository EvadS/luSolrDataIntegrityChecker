package ua.lz.ep.config;

import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.core.converter.ModelConverters;
import ua.lz.ep.dto.response.ApiErrorResponse;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;

@Configuration
public class OpenApiConfig {

    private final BuildProperties buildProperties;

    public OpenApiConfig(BuildProperties buildProperties) {
        this.buildProperties = buildProperties;
    }

    @Bean
    public OpenAPI luSolrCorrectionOpenAPI() {
        Components components = new Components();

        // ensure ApiErrorResponse schema is available under components.schemas so references resolve
        Map<String, Schema> schemas = ModelConverters.getInstance().read(ApiErrorResponse.class);
        if (schemas != null) {
            schemas.forEach(components::addSchemas);
        }

        components
                .addResponses(OpenApiConstants.BAD_REQUEST_RESPONSE, errorResponse("The request is invalid or cannot be processed."))
                .addResponses(OpenApiConstants.NOT_FOUND_RESPONSE, errorResponse("The requested resource was not found."))
                .addResponses(OpenApiConstants.INTERNAL_SERVER_ERROR_RESPONSE, errorResponse("Unexpected internal server error."));

        return new OpenAPI()
                .servers(List.of(new Server()
                        .url("/")
                        .description("Current application server")))
                .components(components)
                .info(new Info()
                        .title("luSolrCorrectionService API")
                        .version(buildProperties.getVersion())
                        .description("REST API for launching Solr correction tasks, tracking execution status and managing task lifecycle.")
                        .contact(new Contact()
                                .name("luSolrCorrectionService team"))
                        .license(new License()
                                .name("Internal Use")))
                .externalDocs(new ExternalDocumentation()
                        .description("OpenAPI JSON specification")
                        .url("/v3/api-docs"));
    }

    private ApiResponse errorResponse(String description) {
        return new ApiResponse()
                .description(description)
                .content(new Content().addMediaType("application/json",
                        new MediaType().schema(new Schema<>().$ref("#/components/schemas/ApiErrorResponse"))));
    }
}

