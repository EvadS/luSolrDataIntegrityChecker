package ua.lz.ep.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "correction")
public class CorrectionProperties {

    /** Maximum number of threads used for parallel processing */
    private int maxThreads = 4;

    /** Number of retries per document when transient errors occur */
    private int maxRetries = 2;
}
