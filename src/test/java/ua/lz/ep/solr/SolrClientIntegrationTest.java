package ua.lz.ep.solr;

import org.apache.solr.client.solrj.SolrClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import ua.lz.ep.config.SolrProperties;
import ua.lz.ep.service.SolrService;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest
@Tag("docker")
class SolrClientIntegrationTest {

    private static final String COLLECTION = "test-collection";

    @Container
    static final GenericContainer<?> SOLR = new GenericContainer<>(DockerImageName.parse("solr:9.4"))
            .withExposedPorts(8983)
            .withCommand("solr-precreate", COLLECTION);

    @DynamicPropertySource
    static void registerSolrProperties(DynamicPropertyRegistry registry) {
        registry.add("data.solr.url", () -> "http://" + SOLR.getHost() + ":" + SOLR.getMappedPort(8983) + "/solr");
        registry.add("data.solr.collection1", () -> COLLECTION);
        registry.add("data.solr.edition", () -> COLLECTION);
    }

    @Autowired
    private SolrClient solrClient;

    @Autowired
    private SolrProperties solrProperties;

    @Autowired
    private SolrService solrService;

    @Test
    void pingShouldReturnSuccess() throws Exception {
        int status = solrClient.ping(solrProperties.getCollection1()).getStatus();
        assertThat(status).isZero();
    }

    @Test
    void serviceShouldPingAllConfiguredCollections() {
        assertThat(solrService.pingCollection(solrProperties.getCollection1())).isTrue();
        assertThat(solrService.pingCollection(solrProperties.getEdition())).isTrue();
    }
}

