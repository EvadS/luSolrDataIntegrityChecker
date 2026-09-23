package ua.lz.ep.dto.payload;

/**
 * тип несоответствий которые ищем
 */
public enum CheckingType {
    LOST_EDITION_DOCUMET(1, ""),

    EDITION_ID_MISMATCH(2,"");


    private  int id;
    private String reportFolderName;

    CheckingType(int id, String reportFolderName) {
        this.id = id;
        this.reportFolderName = reportFolderName;
    }

    public int getId() {
        return id;
    }

    public String getReportFolderName() {
        return reportFolderName;
    }
}
