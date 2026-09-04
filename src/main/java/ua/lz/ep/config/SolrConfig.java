package ua.lz.ep.config;

import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


@Configuration
public class SolrConfig {

    @Value("${data.solr.url}")
    private String solrUrl;

    @Value("${data.solr.collection}")
    private String solrCollection;

    @Bean(name = "ipsuSolrClient")
    public SolrClient solrClient() {
        return new HttpSolrClient.Builder(solrUrl).build();
    }

    @Bean(name = "ipsuSolrCollection")
    public String solrCollection() {
        return solrCollection;
    }
}