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
import ua.lz.ep.component.ProgressReporter;
import ua.lz.ep.config.SolrProperties;
import ua.lz.ep.dto.PeriodRequest;
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
        ProgressReporter progressReporter = mock(ProgressReporter.class);
        SolrPingResponse successResponse = mock(SolrPingResponse.class);
        when(successResponse.getStatus()).thenReturn(0);
        when(solrClient.ping("collection1")).thenReturn(successResponse);
        when(solrClient.ping("editions")).thenReturn(successResponse);

        SolrService service = new SolrServiceImpl(solrClient, solrProperties(),
                new MockEnvironment().withProperty("spring.profiles.active", "test"), testExecutor(), progressReporter);

        assertThat(service.pingCollection("collection1")).isTrue();
        assertThat(service.pingCollection("editions")).isTrue();
    }

    @Test
    void shouldFailFastWhenCollectionConnectionCannotBeEstablished() throws Exception {
        SolrClient solrClient = mock(SolrClient.class);
        ProgressReporter progressReporter = mock(ProgressReporter.class);
        SolrPingResponse successResponse = mock(SolrPingResponse.class);
        when(successResponse.getStatus()).thenReturn(0);
        when(solrClient.ping("collection1")).thenReturn(successResponse);
        when(solrClient.ping("editions")).thenThrow(new IOException("edition unreachable"));

        assertThatThrownBy(() -> new SolrServiceImpl(solrClient, solrProperties(),
                new MockEnvironment().withProperty("spring.profiles.active", "test"), testExecutor(), progressReporter))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("editions");
    }

    @Test
    void pingAllCollectionsShouldReturnFalseWhenOnePingFailsAfterInitialization() throws Exception {
        SolrClient solrClient = mock(SolrClient.class);
        ProgressReporter progressReporter = mock(ProgressReporter.class);
        SolrPingResponse successResponse = mock(SolrPingResponse.class);
        when(successResponse.getStatus()).thenReturn(0);
        when(solrClient.ping("collection1")).thenReturn(successResponse);
        when(solrClient.ping("editions")).thenReturn(successResponse).thenThrow(new IOException("edition unreachable"));

        SolrService service = new SolrServiceImpl(solrClient, solrProperties(),
                new MockEnvironment().withProperty("spring.profiles.active", "test"), testExecutor(),progressReporter);

        assertThat(service.pingCollection("collection1")).isTrue();
        assertThat(service.pingCollection("editions")).isFalse();
    }

    @Test
    void findBrokenEditionShouldReadBatchesBy5000AndQueryUntilTail() throws Exception {
        SolrClient solrClient = mock(SolrClient.class);
        ProgressReporter progressReporter = mock(ProgressReporter.class);
        SolrPingResponse successResponse = mock(SolrPingResponse.class);
        when(successResponse.getStatus()).thenReturn(0);
        when(solrClient.ping("collection1")).thenReturn(successResponse);
        when(solrClient.ping("editions")).thenReturn(successResponse);

        QueryResponse firstBatch = queryResponseWithSize(5000);
        QueryResponse secondBatch = queryResponseWithSize(2);
        when(solrClient.query(eq("collection1"), any())).thenReturn(firstBatch, secondBatch);

        SolrService service = new SolrServiceImpl(solrClient, solrProperties(),
                new MockEnvironment().withProperty("spring.profiles.active", "test"), testExecutor(),
                progressReporter);

        SolrQuery solrQuery = new SolrQuery();
        solrQuery.setQuery("id:*");
//todo:
        PeriodRequest periodRequest = validCorrectionRequest();
        service.findBrokenEdition(periodRequest);

        verify(solrClient, times(2)).query(eq("collection1"), any());
    }

    @Test
    void findBrokenEditionShouldRejectBlankRequest() throws Exception {
        SolrClient solrClient = mock(SolrClient.class);
        SolrPingResponse successResponse = mock(SolrPingResponse.class);
        ProgressReporter progressReporter = mock(ProgressReporter.class);

        when(successResponse.getStatus()).thenReturn(0);
        when(solrClient.ping("collection1")).thenReturn(successResponse);
        when(solrClient.ping("editions")).thenReturn(successResponse);

        SolrService service = new SolrServiceImpl(solrClient, solrProperties(),
                new MockEnvironment().withProperty("spring.profiles.active", "test"), testExecutor(),progressReporter);
        PeriodRequest periodRequest = new PeriodRequest();

        assertThatThrownBy(() -> service.findBrokenEdition(periodRequest))
                .isInstanceOf(NullPointerException.class)
              //  .hasMessageContaining("correctionType")
        ;
    }

    @Test
    void findBrokenEditionShouldAllowOpenEndedPeriod() throws Exception {
        SolrClient solrClient = mock(SolrClient.class);
        ProgressReporter progressReporter = mock(ProgressReporter.class);
        SolrPingResponse successResponse = mock(SolrPingResponse.class);
        when(successResponse.getStatus()).thenReturn(0);
        when(solrClient.ping("collection1")).thenReturn(successResponse);
        when(solrClient.ping("editions")).thenReturn(successResponse);
        when(solrClient.query(eq("collection1"), any())).thenReturn(queryResponseWithSize(0));

        SolrService service = new SolrServiceImpl(solrClient, solrProperties(),
                new MockEnvironment().withProperty("spring.profiles.active", "test"), testExecutor(), progressReporter);

        PeriodRequest periodRequest = new PeriodRequest();
        periodRequest.setCorrectionType(CorrectionType.ONLY_DOCUMENT_ID);
        periodRequest.setStartPeriod(LocalDateTime.now().minusDays(1));

        assertThat(service.findBrokenEdition(periodRequest)).isEmpty();
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

    private PeriodRequest validCorrectionRequest() {
        PeriodRequest periodRequest = new PeriodRequest();
        periodRequest.setCorrectionType(CorrectionType.ONLY_DOCUMENT_ID);
        periodRequest.setStartPeriod(LocalDateTime.now().minusDays(1));
        periodRequest.setEndPeriod(LocalDateTime.now());
        return periodRequest;
    }
}

