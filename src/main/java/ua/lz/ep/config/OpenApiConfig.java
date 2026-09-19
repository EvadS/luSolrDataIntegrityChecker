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
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    private final BuildProperties buildProperties;

    public OpenApiConfig(BuildProperties buildProperties) {
        this.buildProperties = buildProperties;
    }

    @Bean
    public OpenAPI luSolrCorrectionOpenAPI() {
        return new OpenAPI()
                .servers(List.of(new Server()
                        .url("/")
                        .description("Current application server")))
                .components(new Components()
                        .addSchemas("ApiErrorResponse", new Schema<>()
                                .type("object")
                                .description("Unified error response returned by the API")
                                .addProperties("status", new Schema<>().type("integer").description("HTTP status code").example(400))
                                .addProperties("error", new Schema<>().type("string").description("HTTP status reason").example("Bad Request"))
                                .addProperties("message", new Schema<>().type("string").description("Human-readable error message").example("correctionType is required"))
                                .addProperties("path", new Schema<>().type("string").description("Request path that produced the error").example("/api/correction/tasks"))
                                .addProperties("timestamp", new Schema<>().type("string").format("date-time").description("Timestamp when the error occurred").example("2026-09-19T10:15:30"))
                                .required(List.of("status", "error", "message", "path", "timestamp")))
                        .addResponses(OpenApiConstants.BAD_REQUEST_RESPONSE, errorResponse("The request is invalid or cannot be processed."))
                        .addResponses(OpenApiConstants.NOT_FOUND_RESPONSE, errorResponse("The requested resource was not found."))
                        .addResponses(OpenApiConstants.INTERNAL_SERVER_ERROR_RESPONSE, errorResponse("Unexpected internal server error.")))
                .info(new Info()
                        .title("luSolrCorrectionService API")
                        .version(buildProperties.getVersion())
                        .description(buildProperties.getName())
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

