package ua.lz.ep.service;

import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.client.solrj.response.SolrPingResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.util.NamedList;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import ua.lz.ep.config.SolrProperties;
import ua.lz.ep.dto.CorrectionRequest;
import ua.lz.ep.payload.enums.CorrectionType;

import java.io.IOException;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SolrServiceImplTest {

    @Test
    void shouldCreateServiceAndPingAllConfiguredCollections() throws Exception {
        SolrClient solrClient = mock(SolrClient.class);
        SolrPingResponse successResponse = mock(SolrPingResponse.class);
        when(successResponse.getStatus()).thenReturn(0);
        when(solrClient.ping("collection1")).thenReturn(successResponse);
        when(solrClient.ping("editions")).thenReturn(successResponse);

        SolrService service = new SolrServiceImpl(solrClient, solrProperties(), new MockEnvironment().withProperty("spring.profiles.active", "test"), testExecutor());

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

        assertThatThrownBy(() -> new SolrServiceImpl(solrClient, solrProperties(), new MockEnvironment().withProperty("spring.profiles.active", "test"), testExecutor()))
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

        SolrService service = new SolrServiceImpl(solrClient, solrProperties(), new MockEnvironment().withProperty("spring.profiles.active", "test"), testExecutor());

        assertThat(service.pingCollection("collection1")).isTrue();
        assertThat(service.pingCollection("editions")).isFalse();
    }

    @Test
    void correctionProcessingShouldReadBatchesBy5000AndQueryUntilTail() throws Exception {
        SolrClient solrClient = mock(SolrClient.class);
        SolrPingResponse successResponse = mock(SolrPingResponse.class);
        when(successResponse.getStatus()).thenReturn(0);
        when(solrClient.ping("collection1")).thenReturn(successResponse);
        when(solrClient.ping("editions")).thenReturn(successResponse);

        QueryResponse firstBatch = queryResponseWithSize(5000);
        QueryResponse secondBatch = queryResponseWithSize(2);
        when(solrClient.query(eq("collection1"), any())).thenReturn(firstBatch, secondBatch);

        SolrService service = new SolrServiceImpl(solrClient, solrProperties(), new MockEnvironment().withProperty("spring.profiles.active", "test"), testExecutor());

        SolrQuery solrQuery = new SolrQuery();
        solrQuery.setQuery("id:*");
//todo:
        CorrectionRequest correctionRequest = validCorrectionRequest();
        service.correctionProcessing(correctionRequest);

        verify(solrClient, times(2)).query(eq("collection1"), any());
    }

    @Test
    void correctionProcessingShouldRejectBlankRequest() throws Exception {
        SolrClient solrClient = mock(SolrClient.class);
        SolrPingResponse successResponse = mock(SolrPingResponse.class);
        when(successResponse.getStatus()).thenReturn(0);
        when(solrClient.ping("collection1")).thenReturn(successResponse);
        when(solrClient.ping("editions")).thenReturn(successResponse);

        SolrService service = new SolrServiceImpl(solrClient, solrProperties(), new MockEnvironment().withProperty("spring.profiles.active", "test"), testExecutor());
        CorrectionRequest correctionRequest = new CorrectionRequest();

//        assertThatThrownBy(() -> service.correctionProcessing(new SolrQuery("   ")))
//                .isInstanceOf(IllegalArgumentException.class)
//                .hasMessageContaining("must not be blank");

        assertThatThrownBy(() -> service.correctionProcessing(correctionRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be blank");
    }

    private QueryResponse queryResponseWithSize(int size) {
        SolrDocumentList documentList = new SolrDocumentList();
        for (int i = 0; i < size; i++) {
            SolrDocument document = new SolrDocument();
            document.setField("id", "doc-" + i);
            documentList.add(document);
        }

        QueryResponse queryResponse = new QueryResponse();
        NamedList<Object> response = new NamedList<>();
        response.add("response", documentList);
        queryResponse.setResponse(response);
        return queryResponse;
    }

    private ThreadPoolTaskExecutor testExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(10);
        executor.initialize();
        return executor;
    }

    private SolrProperties solrProperties() {
        SolrProperties solrProperties = new SolrProperties();
        solrProperties.setUrl("http://localhost:8983/solr");
        solrProperties.setCollection1("collection1");
        solrProperties.setEdition("editions");
        return solrProperties;
    }

    private CorrectionRequest validCorrectionRequest() {
        CorrectionRequest correctionRequest = new CorrectionRequest();
        correctionRequest.setCorrectionType(CorrectionType.ONLY_DOCUMENT_ID);
        correctionRequest.setStartPeriod(LocalDateTime.now().minusDays(1));
        correctionRequest.setEndPeriod(LocalDateTime.now());
        return correctionRequest;
    }
}

