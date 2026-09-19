package ua.lz.ep.service;

import ua.lz.ep.dto.PeriodRequest;

import java.util.List;

public interface SolrService {
   boolean pingCollection(String collectionName);

    List<String> findBrokenEdition(PeriodRequest periodRequest);
}

