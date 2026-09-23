package ua.lz.ep.config;

import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


@Configuration
public class SolrConfig {

    private static final int SOLR_CONNECTION_TIMEOUT_MS = 10_000;
    private static final int SOLR_SOCKET_TIMEOUT_MS = 60_000;

    @Bean
    @ConfigurationProperties(prefix = "data.solr")
    public SolrProperties solrProperties() {
        return new SolrProperties();
    }

    @Bean(name = "ipsuSolrClient")
    public SolrClient solrClient(SolrProperties solrProperties) {
        return new HttpSolrClient.Builder(solrProperties.getUrl()).build();
    }
}
