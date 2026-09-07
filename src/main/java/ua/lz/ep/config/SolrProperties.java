package ua.lz.ep.config;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@ConfigurationProperties(prefix = "data.solr")
public class SolrProperties {

    @NotBlank
    private String url;

    @NotBlank
    private String collection1;

    @NotBlank
    private String edition;
}



