package io.atworks.apiintelligence.adapter.source;

public final class SourceWorkspaceException extends RuntimeException {

    private final String code;

    public SourceWorkspaceException(String code, String message) {
        super(message);
        this.code = code;
    }

    public SourceWorkspaceException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
