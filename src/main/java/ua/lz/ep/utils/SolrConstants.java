package ua.lz.ep.utils;

import java.time.format.DateTimeFormatter;

public final class SolrConstants {

    public static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ISO_INSTANT;
    public static final String MATCH_ALL_QUERY = "*:*";
    public static final String FIELD_ID = "id";
    public static final String FIELD_CURRENT_EDITION = "f_current_edition";
    public static final String FIELD_EDITION_LIST_IDS = "f_edition_list_ids";
    public static final String FIELD_DATE_MODIFICATION = "f_date_modification";

    private SolrConstants() {
    }
}

