package ua.lz.ep.utils;

import org.apache.solr.client.solrj.SolrQuery;
import org.junit.jupiter.api.Test;
import ua.lz.ep.dto.PeriodRequest;
import ua.lz.ep.payload.enums.CorrectionType;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SolrUtilsTest {

    @Test
    void createMissingEditionSolrQueryShouldCombineDocumentIdsAndFullTimeRange() {
        PeriodRequest periodRequest = new PeriodRequest();
        periodRequest.setCorrectionType(CorrectionType.ONLY_DOCUMENT_ID);
        periodRequest.setStartPeriod(LocalDateTime.of(2026, 1, 9, 0, 0, 0, 365_000_000));
        periodRequest.setEndPeriod(LocalDateTime.of(2026, 7, 9, 23, 59, 0));
        periodRequest.setDocumentIds(java.util.List.of("doc-1", "doc-2"));

        SolrQuery query = SolrUtils.createMissingEditionSolrQuery(periodRequest);

        assertThat(query.getQuery())
                .isEqualTo("id:(\"doc\\-1\" OR \"doc\\-2\") AND f_date_modification:[2026-01-09T00:00:00.365Z TO 2026-07-09T23:59:00Z]");
        assertThat(query.getFields()).contains("id", "f_current_edition", "f_edition_list_ids");
    }

    @Test
    void createMissingEditionSolrQueryShouldUseOpenEndedPeriodWhenOnlyStartIsProvided() {
        PeriodRequest periodRequest = new PeriodRequest();
        periodRequest.setStartPeriod(LocalDateTime.of(2026, 1, 9, 0, 0, 0, 365_000_000));

        SolrQuery query = SolrUtils.createMissingEditionSolrQuery(periodRequest);

        assertThat(query.getQuery())
                .isEqualTo("f_date_modification:[2026-01-09T00:00:00.365Z TO *]");
    }

    @Test
    void createMissingEditionSolrQueryShouldUseOpenEndedPeriodWhenOnlyEndIsProvided() {
        PeriodRequest periodRequest = new PeriodRequest();
        periodRequest.setEndPeriod(LocalDateTime.of(2026, 7, 9, 23, 59, 0));

        SolrQuery query = SolrUtils.createMissingEditionSolrQuery(periodRequest);

        assertThat(query.getQuery())
                .isEqualTo("f_date_modification:[* TO 2026-07-09T23:59:00Z]");
    }

    @Test
    void createMissingEditionSolrQueryShouldUseDocumentIdsWhenPeriodIsMissing() {
        PeriodRequest periodRequest = new PeriodRequest();
        periodRequest.setDocumentIds(java.util.List.of("doc-1", " ", "doc-2"));

        SolrQuery query = SolrUtils.createMissingEditionSolrQuery(periodRequest);

        assertThat(query.getQuery())
                .isEqualTo("id:(\"doc\\-1\" OR \"doc\\-2\")");
    }

    @Test
    void createMissingEditionSolrQueryShouldFallbackToMatchAllWhenNoFiltersAreProvided() {
        PeriodRequest periodRequest = new PeriodRequest();

        SolrQuery query = SolrUtils.createMissingEditionSolrQuery(periodRequest);

        assertThat(query.getQuery()).isEqualTo("*:*");
    }

    @Test
    void createMissingEditionSolrQueryShouldRejectReversedPeriod() {
        PeriodRequest periodRequest = new PeriodRequest();
        periodRequest.setStartPeriod(LocalDateTime.of(2026, 7, 9, 23, 59, 0));
        periodRequest.setEndPeriod(LocalDateTime.of(2026, 1, 9, 0, 0, 0, 365_000_000));

        assertThatThrownBy(() -> SolrUtils.createMissingEditionSolrQuery(periodRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("startPeriod");
    }
}

