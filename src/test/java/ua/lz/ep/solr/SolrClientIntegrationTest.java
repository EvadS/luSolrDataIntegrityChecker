package ua.lz.ep.solr;

import org.apache.solr.client.solrj.SolrClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

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
        registry.add("data.solr.collection", () -> COLLECTION);
    }

    @Autowired
    private SolrClient solrClient;

    @Autowired
    @Qualifier("ipsuSolrCollection")
    private String solrCollection;

    @Test
    void pingShouldReturnSuccess() throws Exception {
        int status = solrClient.ping(solrCollection).getStatus();
        assertThat(status).isZero();
    }
}

