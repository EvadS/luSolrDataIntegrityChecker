package ua.lz.ep;

import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.response.SolrPingResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = "spring.main.allow-bean-definition-overriding=true")
@Import(LuSolrCorrectionServiceApplicationTests.TestSolrClientConfig.class)
class LuSolrCorrectionServiceApplicationTests {

    @Test
    void contextLoads() {
    }

    @TestConfiguration
    static class TestSolrClientConfig {

        @Bean(name = "ipsuSolrClient")
        SolrClient solrClient() throws Exception {
            SolrClient solrClient = mock(SolrClient.class);
            SolrPingResponse response = mock(SolrPingResponse.class);

            when(response.getStatus()).thenReturn(0);
            when(solrClient.ping(anyString())).thenReturn(response);

            return solrClient;
        }
    }

}
