package ua.lz.ep.service;

import ua.lz.ep.dto.PeriodRequest;
import ua.lz.ep.payload.ProcessingTask;

import java.util.List;

public interface SolrService {
   boolean pingCollection(String collectionName);

    List<String> findBrokenEdition(PeriodRequest periodRequest);

    List<String> findBrokenEdition(PeriodRequest periodRequest, ProcessingTask processingTask);

    public List<String> searchEditionIdMismatches(PeriodRequest periodRequest, ProcessingTask processingTask);
}

