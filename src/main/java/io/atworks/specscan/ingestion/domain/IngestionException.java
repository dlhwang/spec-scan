package io.atworks.specscan.ingestion.domain;

public class IngestionException extends RuntimeException {
    private final IngestionErrorCode errorCode;

    public IngestionException(IngestionErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public IngestionException(IngestionErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public IngestionErrorCode getErrorCode() {
        return errorCode;
    }
}
