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
import ua.lz.ep.dto.EditionListDiff;
import ua.lz.ep.dto.PeriodRequest;
import ua.lz.ep.payload.ProcessingTask;
import ua.lz.ep.payload.enums.CorrectionType;
import ua.lz.ep.payload.enums.EditionDiffResultType;
import ua.lz.ep.utils.EditionUtils;
import ua.lz.ep.utils.MappingFieldsHelper;
import ua.lz.ep.utils.SolrConstants;
import ua.lz.ep.utils.SolrUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Log4j2
@Service
public class SolrServiceImpl implements SolrService {

    private static final int BATCH_SIZE = 5000;
    private final SolrClient solrClient;
    private final SolrProperties solrProperties;
    private final Environment environment;
    private final ThreadPoolTaskExecutor correctionTaskExecutor;
    private final ua.lz.ep.config.CorrectionProperties correctionProperties;

    private final ProgressReporter progressReporter;

    // simple metrics exposed for tests: processed count, failures and total latency
    private final AtomicInteger processedCount = new AtomicInteger(0);
    private final AtomicInteger failuresCount = new AtomicInteger(0);
    private final AtomicLong totalLatencyMs = new AtomicLong(0L);

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

    /**
     * Выполняет ping указанной коллекции Solr и возвращает true при успешном ответе.
     *
     * @param collectionName имя коллекции Solr, указанное в конфигурации
     * @return true если Solr доступен и вернул статус 0, иначе false
     */
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


    /**
     * Поиск документов с несуществующими редакции
     *
     * @param periodRequest  критерии поиска
     * @param processingTask объект для обновления прогресса (может быть null)
     * @return список идентификаторов несуществующих редакций или документов
     */

    // todo: A1
    @Override
    public List<String> findBrokenEdition(PeriodRequest periodRequest, ProcessingTask processingTask) {
        List<String> missingEditionIds = new ArrayList<>();
        int currentPositions = 0;
        int totalDocuments;
        int currentItem = 0;

        SolrQuery solrQuery = SolrUtils.createMissingEditionSolrQuery(periodRequest);
        totalDocuments = getDocumentsNumber(solrQuery);
        updateDocumentInProcessingTask(processingTask, totalDocuments);

        log.info("Start processing for {} documents", totalDocuments);
        while (true) {
            throwIfInterrupted("Correction processing was cancelled before reading the next batch");

            SolrDocumentList batchDocuments = executeBatchQuery(solrQuery, currentPositions);
            if (batchDocuments == null || batchDocuments.isEmpty()) {
                break;
            }

            int batchSize = batchDocuments.size();

            // Prepare futures for parallel processing while preserving order
            ThreadPoolExecutor underlying = correctionTaskExecutor.getThreadPoolExecutor();
            Executor executor = underlying;

            List<CompletableFuture<List<String>>> futures = new ArrayList<>(batchSize);

            for (SolrDocument doc : batchDocuments) {
                throwIfInterrupted("Correction processing was cancelled while iterating documents");

                final SolrDocument curDoc = doc;
                CompletableFuture<List<String>> f;
                try {
                    f = CompletableFuture.supplyAsync(() -> {
                        long start = System.nanoTime();
                        String id = null;
                        try {
                            id = extractIdSafely(curDoc);
                            return processMissingEditionsDocument(curDoc, periodRequest.getCorrectionType());
                        } catch (CancellationException e) {
                            throw e;
                        } catch (Exception e) {
                            log.warn("Error processing doc {}: {}", id == null ? "<unknown>" : id, e.getMessage());
                            failuresCount.incrementAndGet();
                            return List.of(id == null ? "<unknown>" : id);
                        } finally {
                            long end = System.nanoTime();
                            long latencyMs = (end - start) / 1_000_000;
                            totalLatencyMs.addAndGet(latencyMs);
                            processedCount.incrementAndGet();
                        }
                    }, executor);
                } catch (RejectedExecutionException ree) {
                    // fallback: executor rejected — run processing in caller thread synchronously
                    long start = System.nanoTime();
                    String id = null;
                    try {
                        id = extractIdSafely(curDoc);
                        List<String> syncResult = processMissingEditionsDocument(curDoc, periodRequest.getCorrectionType());
                        f = java.util.concurrent.CompletableFuture.completedFuture(syncResult);
                    } catch (Exception e) {
                        log.warn("Error processing doc {}: {}", id == null ? "<unknown>" : id, e.getMessage());
                        failuresCount.incrementAndGet();
                        f = CompletableFuture.completedFuture(List.of(id == null ? "<unknown>" : id));
                    } finally {
                        long end = System.nanoTime();
                        long latencyMs = (end - start) / 1_000_000;
                        totalLatencyMs.addAndGet(latencyMs);
                        processedCount.incrementAndGet();
                    }
                }

                futures.add(f);
            }

            // Wait for completion and preserve order by iterating futures in same order
            for (java.util.concurrent.CompletableFuture<java.util.List<String>> future : futures) {
                try {
                    java.util.List<String> editions = future.join();
                    if (editions != null && !editions.isEmpty()) {
                        missingEditionIds.addAll(editions);
                    }
                } catch (CancellationException e) {
                    log.error("Processing was cancelled while waiting for a document to finish", e);
                    Thread.currentThread().interrupt();
                    throw e;
                } catch (Exception e) {
                    log.error("Unexpected error while processing document batch", e);
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

            currentPositions += batchDocuments.size();
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

        return missingEditionIds;
    }

    @Override
    public List<String> findEditionIdsDiffs(EditionListDiff editionListDiff, ProcessingTask processingTask) {
        List<String> missMatchingEditionList = new ArrayList<>();
        int currentPositions = 0;
        int totalDocuments;
        int currentItem = 0;

        SolrQuery solrQuery = SolrUtils.createEditionIdsRequest(editionListDiff);
        totalDocuments = getDocumentsNumber(solrQuery);
        updateDocumentInProcessingTask(processingTask, totalDocuments);

        log.info("Start editions-diff processing for {} documents", totalDocuments);
        while (true) {
            throwIfInterrupted("Editions-diff processing was cancelled before reading the next batch");

            SolrDocumentList batchDocuments = executeBatchQuery(solrQuery, currentPositions);
            if (batchDocuments == null || batchDocuments.isEmpty()) {
                break;
            }

            int batchSize = batchDocuments.size();

            // Prepare futures for parallel processing while preserving order
            ThreadPoolExecutor underlying = correctionTaskExecutor.getThreadPoolExecutor();
            Executor executor = underlying;

            List<CompletableFuture<List<String>>> futures = new ArrayList<>(batchSize);

            for (SolrDocument doc : batchDocuments) {
                throwIfInterrupted("Editions-diff processing was cancelled while iterating documents");

                final SolrDocument curDoc = doc;

                CompletableFuture<List<String>> f;
                try {
                    f = CompletableFuture.supplyAsync(() -> {
                        long start = System.nanoTime();
                        String id = null;
                        try {
                            id = extractIdSafely(curDoc);
                            return processEditionDiffIds(curDoc, editionListDiff.getDiffResultType());
                        } catch (CancellationException e) {
                            throw e;
                        } catch (Exception e) {
                            log.warn("Error processing doc {}: {}", id == null ? "<unknown>" : id, e.getMessage());
                            failuresCount.incrementAndGet();
                            return java.util.List.of(id == null ? "<unknown>" : id);
                        } finally {
                            long end = System.nanoTime();
                            long latencyMs = (end - start) / 1_000_000;
                            totalLatencyMs.addAndGet(latencyMs);
                            processedCount.incrementAndGet();
                        }
                    }, executor);
                } catch (java.util.concurrent.RejectedExecutionException ree) {
                    long start = System.nanoTime();
                    String id = null;
                    try {
                        id = extractIdSafely(curDoc);
                        java.util.List<String> syncResult = processEditionDiffIds(curDoc, editionListDiff.getDiffResultType());
                        f = java.util.concurrent.CompletableFuture.completedFuture(syncResult);
                    } catch (Exception e) {
                        log.warn("Error processing doc {}: {}", id == null ? "<unknown>" : id, e.getMessage());
                        failuresCount.incrementAndGet();
                        f = java.util.concurrent.CompletableFuture.completedFuture(java.util.List.of(id == null ? "<unknown>" : id));
                    } finally {
                        long end = System.nanoTime();
                        long latencyMs = (end - start) / 1_000_000;
                        totalLatencyMs.addAndGet(latencyMs);
                        processedCount.incrementAndGet();
                    }
                }

                futures.add(f);
            }

            // Wait for completion and preserve order by iterating futures in same order
            for (int fi = 0; fi < futures.size(); fi++) {
                java.util.concurrent.CompletableFuture<java.util.List<String>> future = futures.get(fi);
                try {
                    java.util.List<String> editions = future.join();
                    if (editions != null && !editions.isEmpty()) {
                        missMatchingEditionList.addAll(editions);
                    }
                } catch (CancellationException e) {
                    log.error("Processing was cancelled while waiting for a document to finish", e);
                    // Cancel remaining futures to avoid leaving tasks running in executor after cancellation
                    for (int cj = fi + 1; cj < futures.size(); cj++) {
                        java.util.concurrent.CompletableFuture<java.util.List<String>> toCancel = futures.get(cj);
                        if (!toCancel.isDone() && !toCancel.isCancelled()) {
                            try {
                                toCancel.cancel(true);
                            } catch (Exception cancelEx) {
                                log.warn("Failed to cancel future #{}: {}", cj, cancelEx.getMessage());
                            }
                        }
                    }
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

            currentPositions += batchDocuments.size();
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

        return missMatchingEditionList;
    }

    private List<String> processEditionIDsWithRetries(SolrDocument curDoc, EditionDiffResultType diffResultType) {
        long start = System.nanoTime();
        String id = null;
        boolean failed = false;
        List<String> resultList = null;
        try {
            // Ensure id is present and valid before processing; missing id is a bad document and should fail fast
            id = extractIdSafely(curDoc);

            int attempt = 0;
            while (true) {
                attempt++;
                try {
                    resultList = processEditionDiffIds(curDoc, diffResultType);
                    break;
                } catch (CancellationException e) {
                    throw e;
                } catch (Exception e) {
                    log.warn("Error processing doc {} (attempt {}): {}", id, attempt, e.getMessage());
                    if (attempt >= correctionProperties.getMaxRetries()) {
                        log.error("Exceeded retries for doc {} - marking as failed", id, e);
                        failed = true;
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
            return resultList;
        } finally {
            long end = System.nanoTime();
            long latencyMs = (end - start) / 1_000_000;
            totalLatencyMs.addAndGet(latencyMs);
            processedCount.incrementAndGet();
            if (failed) {
                failuresCount.incrementAndGet();
            }
            log.trace("Processed doc {} in {} ms (failed={})", id == null ? "<unknown>" : id, latencyMs, failed);
        }
    }


    private void updateProcessedDocumentInProcessingTask(ProcessingTask processingTask, int currentPositions) {
        if (processingTask != null) {
            processingTask.setDocumentsProcessed(currentPositions);
        }
    }

    /**
     * Safely extract the document id field from SolrDocument.
     * Throws IllegalArgumentException when the id is missing or blank so callers can fail fast with a clear message.
     */
    private String extractIdSafely(SolrDocument doc) {
        if (doc == null) {
            throw new IllegalArgumentException("SolrDocument is null");
        }
        Object idObj = doc.getFieldValue(SolrConstants.FIELD_ID);
        if (idObj == null) {
            throw new IllegalArgumentException("Solr document is missing required field '" + SolrConstants.FIELD_ID + "'");
        }
        String id = idObj.toString();
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Solr document has empty/blank '" + SolrConstants.FIELD_ID + "'");
        }
        return id;
    }

    private void updateDocumentInProcessingTask(ProcessingTask processingTask, int documentsNumber) {
        if (processingTask != null) {
            processingTask.setDocumentsNumber(documentsNumber);
        }
    }
//todo: A1_2
    private List<String> processMissingEditionsDocument(SolrDocument doc, CorrectionType correctionType) {
        String id = extractIdSafely(doc);

        Object editionListObj = doc.getFieldValue(SolrConstants.FIELD_EDITION_LIST_IDS);
        List<String> editionList = MappingFieldsHelper.objectToStringList(editionListObj);

        if (correctionType == CorrectionType.ONLY_DOCUMENT_ID) {
            return listOfNonExistentDocumentIds(id, editionList);
        }
        return listOfNonExistentDocumentEditions(id, editionList);
    }


    // todo: api 2
    private List<String> processEditionDiffIds(SolrDocument doc, EditionDiffResultType diffResultType) {
        List<String> diffIds = new ArrayList<>();

        // Extract id and edition list from document and check editions existence
        String id = extractIdSafely(doc);

        Object editionListObj = doc.getFieldValue(SolrConstants.FIELD_EDITION_LIST_IDS);
        List<String> editionList = MappingFieldsHelper.objectToStringList(editionListObj);

        Object editionListFullObj = doc.getFieldValue(SolrConstants.FIELD_EDITION_LIST_FULL);
        List<String> editionListFullList = MappingFieldsHelper.objectToStringList(editionListFullObj);

        // todo: 1. сейчас проверяем только количество
        if (editionList.size() != editionListFullList.size()) {
            log.info("doc id:{} has edition lists mismatch size. editionList={}, editionListFull={}", id, editionList.size(), editionListFullList.size());
            diffIds.add(id);
        }
//        // Normalize both lists into sets ignoring order, nulls and blank values
//        java.util.Set<String> setA = new java.util.HashSet<>();
//        if (editionList != null) {
//            for (String s : editionList) {
//                if (s != null) {
//                    String t = s.trim();
//                    if (!t.isEmpty()) {
//                        setA.add(t);
//                    }
//                }
//            }
//        }
//
//        java.util.Set<String> setB = new java.util.HashSet<>();
//        if (editionListFullList != null) {
//            for (String s : editionListFullList) {
//                if (s != null) {
//                    String t = s.trim();
//                    if (!t.isEmpty()) {
//                        setB.add(t);
//                    }
//                }
//            }
//        }
//
//        // If sets differ, record the document id as having a mismatch
//        if (!setA.equals(setB)) {
//            log.info("doc id:{} has edition lists mismatch. editionList={}, editionListFull={}", id, setA, setB);
//            diffIds.add(id);
//        }

        return diffIds;
    }

    private List<String> processEditionIds(SolrDocument doc, CorrectionType correctionType) {


        // todo: not implement
        return Collections.emptyList();
    }


    /**
     * поиск редакций на основе списка редакций документа
     * @param id
     * @param editionList
     * @return
     */
    private List<String> listOfNonExistentDocumentIds(String id, List<String> editionList) {
        List<String> missingEditionIds = new ArrayList<>();

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
                    missingEditionIds.add(id);
                    break;
                }
            } catch (SolrServerException e) {
                missingEditionIds.add(id);
                log.debug("Не найдена редакция:{}", editionId);
            } catch (IOException e) {
                log.error("Ошибка при проверке существования издания:{}, {}", editionId, e.getMessage());
            }
        }
        return missingEditionIds;
    }

    private List<String> listOfNonExistentDocumentEditions(String id, List<String> editionList) {
        List<String> missingEditionIds = new ArrayList<>();

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
                    missingEditionIds.add(editionId);
                    break;
                }
            } catch (SolrServerException | IOException e) {
                log.error("Ошибка при проверке существования издания {}: {}", editionId, e.getMessage());
            }
        }
        return missingEditionIds;
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

    /**
     * Возвращает количество обработанных документов (включая успешно и с ошибкой).
     * Используется в тестах и для мониторинга.
     *
     * @return количество обработанных документов
     */
    public int getProcessedCount() {
        return processedCount.get();
    }

    /**
     * Возвращает количество документов, обработка которых завершилась с ошибкой
     * после исчерпания попыток повторов.
     *
     * @return количество неудач при обработке
     */
    public int getFailuresCount() {
        return failuresCount.get();
    }

    /**
     * Средняя задержка обработки одного документа в миллисекундах.
     * Если документов ещё не обрабатывалось, возвращает 0.0.
     *
     * @return средняя задержка в мс
     */
    public double getAverageLatencyMs() {
        int count = processedCount.get();
        return count == 0 ? 0.0 : (double) totalLatencyMs.get() / count;
    }
}
