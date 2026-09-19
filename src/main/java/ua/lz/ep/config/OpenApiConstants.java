package ua.lz.ep.config;

public final class OpenApiConstants {

    public static final String BAD_REQUEST_RESPONSE = "BadRequest";
    public static final String NOT_FOUND_RESPONSE = "NotFound";
    public static final String INTERNAL_SERVER_ERROR_RESPONSE = "InternalServerError";

    public static final String BAD_REQUEST_RESPONSE_REF = "#/components/responses/" + BAD_REQUEST_RESPONSE;
    public static final String NOT_FOUND_RESPONSE_REF = "#/components/responses/" + NOT_FOUND_RESPONSE;
    public static final String INTERNAL_SERVER_ERROR_RESPONSE_REF = "#/components/responses/" + INTERNAL_SERVER_ERROR_RESPONSE;

    private OpenApiConstants() {
    }
}

