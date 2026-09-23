package ua.lz.ep.service;

import ua.lz.ep.dto.PeriodRequest;
import ua.lz.ep.dto.EditionListDiff;
import ua.lz.ep.payload.ProcessingTask;

import java.util.List;

public interface SolrService {
    boolean pingCollection(String collectionName);

    List<String> findBrokenEdition(PeriodRequest periodRequest, ProcessingTask processingTask);

    List<String> findEditionIdsDiffs(EditionListDiff editionListDiff, ProcessingTask processingTask);
}