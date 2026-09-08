package ua.lz.ep.utils;

import org.apache.solr.client.solrj.SolrQuery;
import ua.lz.ep.dto.CorrectionRequest;

import java.util.Optional;

public class SolrUtils {

    private SolrUtils() {
        // Private constructor to prevent instantiation
    }

// todo: implement all cases for solr query creation
    public static SolrQuery createCorrectRequest(CorrectionRequest correctionRequest){
        SolrQuery solrQuery = new SolrQuery();
        solrQuery.addField("id");
        solrQuery.addField("f_current_edition");
        solrQuery.addField("f_edition_list_ids");
       // return solrQuery.setQuery(String.format("%s:[%s TO %s]", "f_date_modification", correctionRequest.getStartPeriod(), correctionRequest.getEndPeriod()));
        return solrQuery.setQuery(String.format("%s:[%s TO %s]", "f_date_modification", "2026-01-09T00:00:00.365Z", "2026-07-09T23:59:00Z"));
    }
}
