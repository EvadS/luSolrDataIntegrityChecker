package ua.lz.ep.utils;

import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.util.ClientUtils;
import ua.lz.ep.dto.PeriodDocsRequest;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class SolrUtils {

    private SolrUtils() {
        // Private constructor to prevent instantiation
    }


    public static SolrQuery createEditionIdsRequest(PeriodDocsRequest periodRequest){
        validatePeriodRequest(periodRequest);

        SolrQuery solrQuery = new SolrQuery();
        solrQuery.addField(SolrConstants.FIELD_ID);
        solrQuery.addField(SolrConstants.FIELD_EDITION_LIST_FULL);
        solrQuery.addField(SolrConstants.FIELD_EDITION_LIST_IDS);

        List<String> filters = new ArrayList<>();
        addDocumentIdsFilter(periodRequest, filters);
        addPeriodFilter(periodRequest, filters);

        String query = filters.isEmpty()
                ? SolrConstants.MATCH_ALL_QUERY
                : String.join(" AND ", filters);

        return solrQuery.setQuery(query);
    }


    public static SolrQuery createMissingEditionSolrQuery(PeriodDocsRequest periodRequest){
        validatePeriodRequest(periodRequest);

       SolrQuery solrQuery = new SolrQuery();
        solrQuery.addField(SolrConstants.FIELD_ID);
        solrQuery.addField(SolrConstants.FIELD_CURRENT_EDITION);
        solrQuery.addField(SolrConstants.FIELD_EDITION_LIST_IDS);

        List<String> filters = new ArrayList<>();
        addDocumentIdsFilter(periodRequest, filters);
        addPeriodFilter(periodRequest, filters);

        String query = filters.isEmpty()
                ? SolrConstants.MATCH_ALL_QUERY
                : String.join(" AND ", filters);

        return solrQuery.setQuery(query);
    }

    private static String formatForSolr(LocalDateTime value) {
        return value.atZone(ZoneOffset.UTC).format(SolrConstants.DATE_TIME_FORMATTER);
    }

    private static void validatePeriodRequest(PeriodDocsRequest periodRequest) {
        if (periodRequest == null) {
            throw new IllegalArgumentException("PeriodDocsRequest must not be null");
        }

        LocalDateTime startPeriod = periodRequest.getStartPeriod();
        LocalDateTime endPeriod = periodRequest.getEndPeriod();
        if (startPeriod != null && endPeriod != null && startPeriod.isAfter(endPeriod)) {
            throw new IllegalArgumentException("startPeriod must be earlier than or equal to endPeriod");
        }
    }

    private static void addDocumentIdsFilter(PeriodDocsRequest periodRequest, List<String> filters) {
        List<String> documentIds = periodRequest.getDocumentIds() == null
                ? List.of()
                : periodRequest.getDocumentIds().stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .map(ClientUtils::escapeQueryChars)
                .map(value -> "\"" + value + "\"")
                .toList();

        if (!documentIds.isEmpty()) {
            filters.add(SolrConstants.FIELD_ID + ":(" + String.join(" OR ", documentIds) + ")");
        }
    }

    private static void addPeriodFilter(PeriodDocsRequest periodRequest, List<String> filters) {
        LocalDateTime startPeriod = periodRequest.getStartPeriod();
        LocalDateTime endPeriod = periodRequest.getEndPeriod();

        if (startPeriod != null && endPeriod != null) {
            filters.add(String.format("%s:[%s TO %s]",
                    SolrConstants.FIELD_DATE_MODIFICATION,
                    formatForSolr(startPeriod),
                    formatForSolr(endPeriod)));
            return;
        }

        if (startPeriod != null) {
            filters.add(String.format("%s:[%s TO *]",
                    SolrConstants.FIELD_DATE_MODIFICATION,
                    formatForSolr(startPeriod)));
            return;
        }

        if (endPeriod != null) {
            filters.add(String.format("%s:[* TO %s]",
                    SolrConstants.FIELD_DATE_MODIFICATION,
                    formatForSolr(endPeriod)));
        }
    }
}
