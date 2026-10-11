package com.first.app.integration;

/**
 * Typed provider failure raised by the provider clients (the single place provider outcomes are
 * interpreted). The services translate it into domain exceptions:
 * {@code INPUT_REJECTION} → 400, {@code UPSTREAM_UNAVAILABLE} → 502.
 */
public class TravelProviderException extends RuntimeException {

    public enum Kind {
        INPUT_REJECTION,
        UPSTREAM_UNAVAILABLE
    }

    private final Kind kind;

    public TravelProviderException(Kind kind, String reason) {
        super(reason);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }

    public String reason() {
        return getMessage();
    }
}
