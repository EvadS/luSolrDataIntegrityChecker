package ua.lz.ep.service;

import ua.lz.ep.dto.CorrectionRequest;

public interface SolrService {
   boolean pingCollection(String collectionName);

   void correctionProcessing(CorrectionRequest correctionRequest);
}

