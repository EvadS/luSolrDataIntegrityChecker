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

    private final ProgressReporter progressReporter;

    public SolrServiceImpl(
            @Qualifier("ipsuSolrClient") SolrClient solrClient,
            SolrProperties solrProperties,
            Environment environment,
            @Qualifier("correctionTaskExecutor") ThreadPoolTaskExecutor correctionTaskExecutor, ProgressReporter progressReporter) {

        this.solrClient = solrClient;
        this.solrProperties = solrProperties;
        this.environment = environment;
        this.correctionTaskExecutor = correctionTaskExecutor;
        this.progressReporter = progressReporter;

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
    public List<String> findBrokenEdition(PeriodRequest periodRequest) {
        return findBrokenEdition(periodRequest, null);
    }

    @Override
    public List<String> findBrokenEdition(PeriodRequest periodRequest, ProcessingTask processingTask) {
        List<String> notExistedEdition = new ArrayList<>();
        int currentPositions = 0;
        int totalDocuments = 0;
        int currentItem = 0;

        SolrQuery solrQuery = SolrUtils.createCorrectRequest(periodRequest);
        totalDocuments = getDocumentsNumber(solrQuery);
        processingTask.setDocumentsNumber(totalDocuments);

        while (true) {
            throwIfInterrupted("Correction processing was cancelled before reading the next batch");

            SolrDocumentList batchDocuments = executeBatchQuery(solrQuery, currentPositions);
            if (batchDocuments == null || batchDocuments.isEmpty()) {
                break;
            }

            List<String> editions = new ArrayList<>();
            for (SolrDocument doc : batchDocuments) {
                throwIfInterrupted("Correction processing was cancelled while iterating documents");

                log.trace("Submitting task for document: {}", doc.get(SolrConstants.FIELD_ID));
                editions = processDocument(doc, periodRequest.getCorrectionType());
                if (!editions.isEmpty()) {
                    notExistedEdition.addAll(editions);
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
}