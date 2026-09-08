package ua.lz.ep.service;

import lombok.extern.log4j.Log4j2;
import lombok.extern.slf4j.Slf4j;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.client.solrj.response.SolrPingResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import ua.lz.ep.config.SolrProperties;
import ua.lz.ep.dto.CorrectionRequest;
import ua.lz.ep.payload.enums.CorrectionType;
import ua.lz.ep.utils.SolrUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;

@Log4j2
@Slf4j
@Service
public class SolrServiceImpl implements SolrService {

    private static final int BATCH_SIZE = 5000;
    private final java.util.concurrent.atomic.AtomicBoolean cancelled = new java.util.concurrent.atomic.AtomicBoolean(false);
    private final SolrClient solrClient;
    private final SolrProperties solrProperties;
    private final Environment environment;
    private final ThreadPoolTaskExecutor correctionTaskExecutor;

    private volatile ExecutorService documentsExecutor;

    public SolrServiceImpl(
            @Qualifier("ipsuSolrClient") SolrClient solrClient,
            SolrProperties solrProperties,
            Environment environment,
            @Qualifier("correctionTaskExecutor") ThreadPoolTaskExecutor correctionTaskExecutor) {

        this.solrClient = solrClient;
        this.solrProperties = solrProperties;
        this.environment = environment;
        this.correctionTaskExecutor = correctionTaskExecutor;

        logConfiguration();
        validateCollectionConnection("collection1", solrProperties.getCollection1());
        validateCollectionConnection("edition", solrProperties.getEdition());
    }

    private void logConfiguration() {
        log.info("Solr configuration: collection1='{}', edition='{}', url='{}', env='{}'",
                solrProperties.getCollection1(),
                solrProperties.getEdition(),
                solrProperties.getUrl(),
                resolveActiveEnvironment());
    }

    private String resolveActiveEnvironment() {
        String[] activeProfiles = environment.getActiveProfiles();
        return activeProfiles.length == 0 ? "default" : String.join(",", Arrays.asList(activeProfiles));
    }

    private void validateCollectionConnection(String collectionPropertyName, String collectionName) {

        if (!pingCollection(collectionName)) {
            throw new IllegalStateException("Failed to establish Solr connection for " + collectionPropertyName + "='" + collectionName + "'");
        }

        log.info("Solr connection established for {}='{}'", collectionPropertyName, collectionName);
    }

    @Override
    public boolean pingCollection(String collectionName) {
        try {
            SolrPingResponse response = solrClient.ping(collectionName);
            return response != null && response.getStatus() == 0;
        } catch (SolrServerException | IOException e) {
            log.error("Solr ping failed for collection='{}'", collectionName, e);
            return false;
        }
    }

    @Override
    public void correctionProcessing(CorrectionRequest correctionRequest ) {
        validateCorrectionRequest(correctionRequest);

        int start = 0;
        int batchNumber = 1;
        List<CompletableFuture<Void>> batchFutures = new ArrayList<>();

        SolrQuery solrQuery = SolrUtils.createCorrectRequest(correctionRequest);

        while (true) {
            //документы для обработки
            SolrDocumentList documents = executeBatchQuery(solrQuery, start);
            if (documents == null || documents.isEmpty()) {
                break;
            }

            for (SolrDocument doc : documents) {
                if (cancelled.get()) {
                    log.info("documentsProcessing was cancelled before submitting all tasks");
                    break;
                }

                final SolrDocument d = doc;
            }

            if (cancelled.get()) {
                break;
            }

            if (documents.size() < BATCH_SIZE) {
                break;
            }

            start += BATCH_SIZE;
            batchNumber++;
        }
    }

    private void validateCorrectionRequest(CorrectionRequest correctionRequest) {
        if (correctionRequest == null
                || correctionRequest.getCorrectionType() == null
                || correctionRequest.getStartPeriod() == null
                || correctionRequest.getEndPeriod() == null) {
            throw new IllegalArgumentException("CorrectionRequest must not be blank");
        }
    }

    private SolrDocumentList executeBatchQuery(SolrQuery query, int start) {
        query.setStart(start);
        query.setRows(BATCH_SIZE);

        try {
            QueryResponse response = solrClient.query(solrProperties.getCollection1(), query);
            return response.getResults();
        } catch (SolrServerException | IOException e) {
            throw new IllegalStateException("Failed to execute Solr request: '" + query.toString() + "'", e);
        }
    }
}