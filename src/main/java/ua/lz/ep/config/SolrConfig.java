package ua.lz.ep.config;

import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


@Configuration
public class SolrConfig {

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