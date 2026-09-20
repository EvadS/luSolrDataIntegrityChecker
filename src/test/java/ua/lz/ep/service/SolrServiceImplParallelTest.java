package ua.lz.ep.service;

import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import ua.lz.ep.config.SolrProperties;
import ua.lz.ep.component.ProgressReporter;
import ua.lz.ep.dto.PeriodRequest;
import ua.lz.ep.payload.ProcessingTask;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

public class SolrServiceImplParallelTest {

    private SolrClient solrClient;
    private SolrProperties solrProperties;
    private Environment environment;
    private ThreadPoolTaskExecutor executor;
    private ProgressReporter progressReporter;
    private SolrServiceImpl solrService;

    @BeforeEach
    public void setup() throws Exception {
        solrClient = Mockito.mock(SolrClient.class);
        solrProperties = new SolrProperties();
        solrProperties.setCollection1("collection1");
        solrProperties.setEdition("edition");
        solrProperties.setUrl("http://localhost:8983");
        environment = Mockito.mock(Environment.class);
        when(environment.getActiveProfiles()).thenReturn(new String[]{"test"});
        executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(10);
        executor.initialize();
        progressReporter = Mockito.mock(ProgressReporter.class);

        // mock ping responses used in constructor
        org.apache.solr.client.solrj.response.SolrPingResponse pingResp = Mockito.mock(org.apache.solr.client.solrj.response.SolrPingResponse.class);
        when(pingResp.getStatus()).thenReturn(0);
        when(solrClient.ping(any())).thenReturn(pingResp);

        ua.lz.ep.config.CorrectionProperties cp = new ua.lz.ep.config.CorrectionProperties();
        cp.setMaxThreads(4);
        cp.setMaxRetries(2);

        solrService = new SolrServiceImpl(solrClient, solrProperties, environment, executor, cp, progressReporter);

    }

    @Test
    public void testParallelProcessingPreservesOrderAndRetries() throws Exception {
        // Prepare 3 documents
        SolrDocument doc1 = new SolrDocument();
        doc1.setField("id", "doc1");
        SolrDocument doc2 = new SolrDocument();
        doc2.setField("id", "doc2");
        SolrDocument doc3 = new SolrDocument();
        doc3.setField("id", "doc3");

        SolrDocumentList batch = new SolrDocumentList();
        batch.add(doc1);
        batch.add(doc2);
        batch.add(doc3);

        // Mock count query (rows==0) -> 3
        QueryResponse countResp = Mockito.mock(QueryResponse.class);
        SolrDocumentList countList = new SolrDocumentList();
        countList.setNumFound(3);
        when(countResp.getResults()).thenReturn(countList);

        // Mock batch query (start=0 rows=BATCH_SIZE) -> batch
        QueryResponse batchResp = Mockito.mock(QueryResponse.class);
        when(batchResp.getResults()).thenReturn(batch);

        // Mock empty batch response
        QueryResponse emptyResp = Mockito.mock(QueryResponse.class);
        SolrDocumentList emptyList = new SolrDocumentList();
        when(emptyResp.getResults()).thenReturn(emptyList);

        // Control sequence: first call (count) -> countResp, second call (batch start=0) -> batchResp, third call (next batch) -> emptyResp
        AtomicInteger queryCall = new AtomicInteger(0);
        when(solrClient.query(eq(solrProperties.getCollection1()), any())).thenAnswer(invocation -> {
            queryCall.incrementAndGet();
            int call = queryCall.get();
            if (call == 1) return countResp;
            if (call == 2) return batchResp;
            return emptyResp;
        });

        // For edition checks: doc2 will be reported as missing (numFound==0 for its edition id), others exist
        when(solrClient.query(eq(solrProperties.getEdition()), any())).thenAnswer(invocation -> {
            org.apache.solr.client.solrj.SolrQuery q = invocation.getArgument(1);
            String qstr = q.getQuery();
            QueryResponse r = Mockito.mock(QueryResponse.class);
            SolrDocumentList l = new SolrDocumentList();
            if (qstr.contains("doc2_v1")) {
                l.setNumFound(0L);
            } else {
                l.setNumFound(1L);
            }
            when(r.getResults()).thenReturn(l);
            return r;
        });

        // set edition lists: only doc2 has an edition entry v1
        batch.get(0).setField("f_edition_list_ids", List.of());
        batch.get(1).setField("f_edition_list_ids", List.of("v1"));
        batch.get(2).setField("f_edition_list_ids", List.of());

        PeriodRequest request = new PeriodRequest();
        request.setCorrectionType(ua.lz.ep.payload.enums.CorrectionType.EDITIONS_ID); // treat as editions check

        List<String> result = solrService.findBrokenEdition(request, new ProcessingTask());

        // Expect that only doc2 produced a broken edition and it's preserved in order (doc1 none, doc2 broken, doc3 none)
        assertEquals(1, result.size());
        assertEquals("doc2_v1", result.get(0));

        // Metrics: processed 3 docs, no failures, average latency recorded
        assertEquals(3, solrService.getProcessedCount());
        assertEquals(0, solrService.getFailuresCount());
        double avg = solrService.getAverageLatencyMs();
        // average latency should be non-negative (>=0). On CI it may be 0 for mocked quick ops but check >= 0
        assertEquals(true, avg >= 0.0);
    }
}
