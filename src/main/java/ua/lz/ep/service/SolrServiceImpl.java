package ua.lz.ep.service;

import lombok.extern.log4j.Log4j2;
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
import ua.lz.ep.component.ProgressReporter;
import ua.lz.ep.config.SolrProperties;
import ua.lz.ep.dto.PeriodRequest;
import ua.lz.ep.payload.ProcessingTask;
import ua.lz.ep.payload.enums.CorrectionType;
import ua.lz.ep.utils.EditionUtils;
import ua.lz.ep.utils.MappingFieldsHelper;
import ua.lz.ep.utils.SolrConstants;
import ua.lz.ep.utils.SolrUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CancellationException;

@Log4j2
@Service
public class SolrServiceImpl implements SolrService {

    private static final int BATCH_SIZE = 500;
    private final SolrClient solrClient;
    private final SolrProperties solrProperties;
    private final Environment environment;
    private final ThreadPoolTaskExecutor correctionTaskExecutor;
    private final ua.lz.ep.config.CorrectionProperties correctionProperties;

    private final ProgressReporter progressReporter;

    // simple metrics exposed for tests: processed count, failures and total latency
    private final java.util.concurrent.atomic.AtomicInteger processedCount = new java.util.concurrent.atomic.AtomicInteger(0);
    private final java.util.concurrent.atomic.AtomicInteger failuresCount = new java.util.concurrent.atomic.AtomicInteger(0);
    private final java.util.concurrent.atomic.AtomicLong totalLatencyMs = new java.util.concurrent.atomic.AtomicLong(0L);

    @org.springframework.beans.factory.annotation.Autowired
    public SolrServiceImpl(
            @Qualifier("ipsuSolrClient") SolrClient solrClient,
            SolrProperties solrProperties,
            Environment environment,
            @Qualifier("correctionTaskExecutor") ThreadPoolTaskExecutor correctionTaskExecutor,
            ua.lz.ep.config.CorrectionProperties correctionProperties,
            ProgressReporter progressReporter) {

        this.solrClient = solrClient;
        this.solrProperties = solrProperties;
        this.environment = environment;
        this.correctionTaskExecutor = correctionTaskExecutor;
        this.correctionProperties = correctionProperties;
        this.progressReporter = progressReporter;

        logConfiguration();
        validateCollectionConnection("collection1", solrProperties.getCollection1());
        validateCollectionConnection("edition", solrProperties.getEdition());
    }

    // Backward-compatible constructor for tests/beans that don't provide CorrectionProperties
    public SolrServiceImpl(
            @Qualifier("ipsuSolrClient") SolrClient solrClient,
            SolrProperties solrProperties,
            Environment environment,
            @Qualifier("correctionTaskExecutor") ThreadPoolTaskExecutor correctionTaskExecutor,
            ProgressReporter progressReporter) {
        this(solrClient, solrProperties, environment, correctionTaskExecutor, new ua.lz.ep.config.CorrectionProperties(), progressReporter);
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
    public List<String> findBrokenEdition(PeriodRequest periodRequest) {
        return findBrokenEdition(periodRequest, null);
    }

    @Override
    public List<String> findBrokenEdition(PeriodRequest periodRequest, ProcessingTask processingTask) {
        List<String> notExistedEdition = new ArrayList<>();
        int currentPositions = 0;
        int totalDocuments;
        int currentItem = 0;

        SolrQuery solrQuery = SolrUtils.createCorrectRequest(periodRequest);
        totalDocuments = getDocumentsNumber(solrQuery);
        updateDocumentInProcessingTask(processingTask, totalDocuments);

        // bounds for parallelism are taken from configured executor and correction properties
        // maxThreads is configured on the ThreadPool bean; retries come from correctionProperties

        while (true) {
            throwIfInterrupted("Correction processing was cancelled before reading the next batch");

            SolrDocumentList batchDocuments = executeBatchQuery(solrQuery, currentPositions);
            if (batchDocuments == null || batchDocuments.isEmpty()) {
                break;
            }

            int batchSize = batchDocuments.size();

            // Prepare futures for parallel processing while preserving order
            java.util.concurrent.Executor executor = correctionTaskExecutor.getThreadPoolExecutor();
            List<java.util.concurrent.CompletableFuture<java.util.List<String>>> futures = new ArrayList<>(batchSize);

            for (SolrDocument doc : batchDocuments) {
                throwIfInterrupted("Correction processing was cancelled while iterating documents");

                final SolrDocument curDoc = doc;
                java.util.concurrent.CompletableFuture<java.util.List<String>> f = java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                    long start = System.nanoTime();
                    String id = curDoc.getFieldValue(SolrConstants.FIELD_ID).toString();
                    boolean failed = false;
                    java.util.List<String> resultList = null;
                    try {
                        // retry loop using configured retries
                        int attempt = 0;
                        while (true) {
                            attempt++;
                            try {
                                resultList = processDocument(curDoc, periodRequest.getCorrectionType());
                                break; // success
                            } catch (CancellationException e) {
                                throw e;
                            } catch (Exception e) {
                                log.warn("Error processing doc {} (attempt {}): {}", id, attempt, e.getMessage());
                                if (attempt >= correctionProperties.getMaxRetries()) {
                                    log.error("Exceeded retries for doc {} - marking as failed", id, e);
                                    failed = true;
                                    // save failed id as a failed result so it will be persisted by caller
                                    resultList = java.util.List.of(id);
                                    break;
                                }
                                try {
                                    Thread.sleep(100L * attempt);
                                } catch (InterruptedException ie) {
                                    Thread.currentThread().interrupt();
                                    throw new CancellationException("Interrupted during retry backoff");
                                }
                            }
                        }
                        return new java.util.AbstractMap.SimpleEntry<java.util.List<String>, Boolean>(resultList, failed);
                    } finally {
                        long end = System.nanoTime();
                        long latencyMs = (end - start) / 1_000_000;
                        totalLatencyMs.addAndGet(latencyMs);
                        processedCount.incrementAndGet();
                        log.debug("Processed doc {} in {} ms", id, latencyMs);
                    }
                }, executor).thenApply(entry -> {
                    if (entry != null && Boolean.TRUE.equals(entry.getValue())) {
                        failuresCount.incrementAndGet();
                    }
                    return entry == null ? null : entry.getKey();
                });

                futures.add(f);
            }

            // Wait for completion and preserve order by iterating futures in same order
            for (java.util.concurrent.CompletableFuture<java.util.List<String>> future : futures) {
                try {
                    java.util.List<String> editions = future.join();
                    if (editions != null && !editions.isEmpty()) {
                        notExistedEdition.addAll(editions);
                    }
                } catch (CancellationException e) {
                    log.warn("Processing was cancelled while waiting for a document to finish", e);
                    Thread.currentThread().interrupt();
                    throw e;
                } catch (Exception e) {
                    log.error("Unexpected error while processing document batch", e);
                    // best-effort: continue with next
                }
            }

            currentItem += batchDocuments.size();
            if (processingTask != null && totalDocuments > 0) {
                int percent = (int) Math.min(100, Math.round((currentItem * 100.0) / totalDocuments));
                processingTask.updateProgress(percent, "Processed " + currentItem + " of " + totalDocuments + " documents");
            }

            if (batchDocuments.size() < BATCH_SIZE) {
                break;
            }

            currentPositions += BATCH_SIZE;
            progressReporter.reportProgress(currentItem, totalDocuments, batchDocuments.size());

            updateProcessedDocumentInProcessingTask(processingTask, currentPositions);
        }

        if (processingTask != null) {
            if (totalDocuments == 0) {
                processingTask.updateProgress(100, "No documents matched the request");
            } else {
                processingTask.updateProgress(100, "Finished processing documents");
            }
        }

        return notExistedEdition;
    }

    private void updateProcessedDocumentInProcessingTask(ProcessingTask processingTask, int currentPositions) {
        if (processingTask != null) {
            processingTask.setDocumentsProcessed(currentPositions);
        }
    }

    private void updateDocumentInProcessingTask(ProcessingTask processingTask, int documentsNumber) {
        if (processingTask != null) {
            processingTask.setDocumentsNumber(documentsNumber);
        }
    }

    private List<String> processDocument(SolrDocument doc, CorrectionType correctionType) {
        String id = doc.getFieldValue(SolrConstants.FIELD_ID).toString();

        Object editionListObj = doc.getFieldValue(SolrConstants.FIELD_EDITION_LIST_IDS);
        List<String> editionList = MappingFieldsHelper.objectToStringList(editionListObj);

        if (correctionType == CorrectionType.ONLY_DOCUMENT_ID) {
            return listOfNonExistentDocumentIds(id, editionList);
        }
        return listOfNonExistentDocumentEditions(id, editionList);

    }


    private List<String> listOfNonExistentDocumentIds(String id, List<String> editionList) {
        List<String> notExistedEdition = new ArrayList<>();

        log.debug("id:{} , editions: {}", id, String.join(",", editionList));
        List<String> editionIds = EditionUtils.buildEditionIds(id, editionList);

        for (String editionId : editionIds) {
            throwIfInterrupted("Correction processing was cancelled while checking document ids");

            SolrQuery query = new SolrQuery();
            query.setQuery(String.format("%s:\"%s\"", SolrConstants.FIELD_ID, editionId));
            query.setRows(0); // Нам не нужны документы, только количество

            try {
                long count = solrClient.query(solrProperties.getEdition(), query).getResults().getNumFound();
                if (count == 0) {
                    log.info("doc id:{},  has broken edition:{}", id, editionId);
                    notExistedEdition.add(id);
                    break;
                }
            } catch (SolrServerException e) {
                notExistedEdition.add(id);
                log.warn("Не найдена редакция:{}", editionId);
            } catch (IOException e) {
                log.error("Ошибка при проверке существования издания:{}, {}", editionId, e.getMessage());
            }
        }
        return notExistedEdition;
    }

    private void validateCorrectionRequest(PeriodRequest periodRequest) {
        if (periodRequest == null
                || periodRequest.getCorrectionType() == null) {
            throw new IllegalArgumentException("PeriodRequest must contain correctionType");
        }

        boolean hasDocumentIds = periodRequest.getDocumentIds() != null
                && periodRequest.getDocumentIds().stream().anyMatch(id -> id != null && !id.isBlank());
        boolean hasPeriodFilter = periodRequest.getStartPeriod() != null || periodRequest.getEndPeriod() != null;

        if (!hasDocumentIds && !hasPeriodFilter) {
            throw new IllegalArgumentException("PeriodRequest must contain documentIds or period boundaries");
        }

        if (periodRequest.getStartPeriod() != null
                && periodRequest.getEndPeriod() != null
                && periodRequest.getStartPeriod().isAfter(periodRequest.getEndPeriod())) {
            throw new IllegalArgumentException("startPeriod must be earlier than or equal to endPeriod");
        }
    }

    private List<String> listOfNonExistentDocumentEditions(String id, List<String> editionList) {
        List<String> notExistedEdition = new ArrayList<>();

        log.debug("id:{} , editions: {}", id, editionList);
        List<String> editionIds = EditionUtils.buildEditionIds(id, editionList);

        for (String editionId : editionIds) {
            throwIfInterrupted("Correction processing was cancelled while checking editions for document " + id);

            SolrQuery query = new SolrQuery();
            query.setQuery(String.format("%s:\"%s\"", SolrConstants.FIELD_ID, editionId));
            query.setRows(0); // Нам не нужны документы, только количество

            try {
                long count = solrClient.query(solrProperties.getEdition(), query).getResults().getNumFound();
                if (count == 0) {
                    log.info("doc id:{}, has broken edition:{}", id, editionId);

                    // todo: редакции
                    notExistedEdition.add(editionId);
                    break;
                }
            } catch (SolrServerException | IOException e) {
                log.error("Ошибка при проверке существования издания {}: {}", editionId, e.getMessage());
            }
        }
        return notExistedEdition;
    }

    private int getDocumentsNumber(SolrQuery countQuery) {
        if (countQuery == null) {
            throw new IllegalArgumentException("SolrQuery must not be null");
        }
        countQuery.setRows(0);

        try {
            QueryResponse response = solrClient.query(solrProperties.getCollection1(), countQuery);
            if (response == null || response.getResults() == null) {
                return 0;
            }
            return (int) Math.min(response.getResults().getNumFound(), Integer.MAX_VALUE);
        } catch (SolrServerException | IOException e) {
            throw new IllegalStateException("Failed to count documents for Solr query: '" + countQuery + "'", e);
        }
    }

    private SolrDocumentList executeBatchQuery(SolrQuery query, int start) {
        throwIfInterrupted("Correction processing was cancelled before Solr batch query");
        query.setStart(start);
        query.setRows(BATCH_SIZE);

        try {
            QueryResponse response = solrClient.query(solrProperties.getCollection1(), query);
            return response.getResults();
        } catch (SolrServerException | IOException e) {
            throw new IllegalStateException("Failed to execute Solr request: '" + query + "'", e);
        }
    }

    private void throwIfInterrupted(String message) {
        if (Thread.currentThread().isInterrupted()) {
            throw new CancellationException(message);
        }
    }

    // Metrics accessors (used by tests)
    public int getProcessedCount() {
        return processedCount.get();
    }

    public int getFailuresCount() {
        return failuresCount.get();
    }

    public double getAverageLatencyMs() {
        int count = processedCount.get();
        return count == 0 ? 0.0 : (double) totalLatencyMs.get() / count;
    }
}
