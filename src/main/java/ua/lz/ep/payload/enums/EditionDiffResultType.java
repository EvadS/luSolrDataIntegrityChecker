package ua.lz.ep.payload.enums;

import lombok.Getter;

@Getter
public enum EditionDiffResultType {
    ONLY_DOC_ID,
    DOC_ID_AND_EDITION_IDS,
    DOC_ID_AND_EDITION_IDS_AND_LIST_FULL
}
