package ua.lz.ep.service;

import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.response.SolrPingResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import ua.lz.ep.config.SolrProperties;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SolrServiceImplTest {

    @Test
    void shouldCreateServiceAndPingAllConfiguredCollections() throws Exception {
        SolrClient solrClient = mock(SolrClient.class);
        SolrPingResponse successResponse = mock(SolrPingResponse.class);
        when(successResponse.getStatus()).thenReturn(0);
        when(solrClient.ping("collection1")).thenReturn(successResponse);
        when(solrClient.ping("editions")).thenReturn(successResponse);

        SolrService service = new SolrServiceImpl(solrClient, solrProperties(), new MockEnvironment().withProperty("spring.profiles.active", "test"));

        assertThat(service.pingCollection("collection1")).isTrue();
        assertThat(service.pingCollection("editions")).isTrue();
    }

    @Test
    void shouldFailFastWhenCollectionConnectionCannotBeEstablished() throws Exception {
        SolrClient solrClient = mock(SolrClient.class);
        SolrPingResponse successResponse = mock(SolrPingResponse.class);
        when(successResponse.getStatus()).thenReturn(0);
        when(solrClient.ping("collection1")).thenReturn(successResponse);
        when(solrClient.ping("editions")).thenThrow(new IOException("edition unreachable"));

        assertThatThrownBy(() -> new SolrServiceImpl(solrClient, solrProperties(), new MockEnvironment().withProperty("spring.profiles.active", "test")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("editions");
    }

    @Test
    void pingAllCollectionsShouldReturnFalseWhenOnePingFailsAfterInitialization() throws Exception {
        SolrClient solrClient = mock(SolrClient.class);
        SolrPingResponse successResponse = mock(SolrPingResponse.class);
        when(successResponse.getStatus()).thenReturn(0);
        when(solrClient.ping("collection1")).thenReturn(successResponse);
        when(solrClient.ping("editions")).thenReturn(successResponse).thenThrow(new IOException("edition unreachable"));

        SolrService service = new SolrServiceImpl(solrClient, solrProperties(), new MockEnvironment().withProperty("spring.profiles.active", "test"));

        assertThat(service.pingCollection("collection1")).isTrue();
        assertThat(service.pingCollection("editions")).isFalse();
    }

    private SolrProperties solrProperties() {
        SolrProperties solrProperties = new SolrProperties();
        solrProperties.setUrl("http://localhost:8983/solr");
        solrProperties.setCollection1("collection1");
        solrProperties.setEdition("editions");
        return solrProperties;
    }
}

