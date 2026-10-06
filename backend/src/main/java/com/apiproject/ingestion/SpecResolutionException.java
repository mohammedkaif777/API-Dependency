package com.apiproject.ingestion;

import java.util.Set;

public final class SpecResolutionException extends RuntimeException {

    public static final String CIRCULAR_REF = "CIRCULAR_REF";
    public static final String UNSUPPORTED_REF = "UNSUPPORTED_REF";
    public static final String UNKNOWN_REF = "UNKNOWN_REF";

    private final String code;

    public SpecResolutionException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    @Override
    public String toString() {
        return "SpecResolutionException(" + code + "): " + getMessage();
    }
}