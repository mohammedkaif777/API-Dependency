package com.apiproject.ingestion;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Thrown when an OpenAPI document cannot be ingested because it is malformed or unsupported. Carries
 * the raw messages emitted by the OpenAPI parser library for diagnostics.
 *
 * <p>Error taxonomy (FR-003):
 * <ul>
 *   <li>{@link #MALFORMED_SPEC} — YAML/JSON syntax broken, required fields missing (e.g.
 *       {@code openapi}, {@code info}, {@code paths}), zero paths to ingest, or a non-3.x document
 *       (the OpenAPI library refuses anything that is not 3.x with a null model, so an
 *       "unsupported version" surfaces here too rather than getting a distinct code).</li>
 * </ul>
 *
 * <p>Codes only — HTTP status mapping (400) is defined in the sequence diagrams but belongs to the
 * API layer, which does not exist yet. Deterministic and pure (NFR-008).
 */
public final class SpecParseException extends RuntimeException {

    public static final String MALFORMED_SPEC = "MALFORMED_SPEC";

    private final String code;
    private final List<String> parserMessages;

    public SpecParseException(String code, String message, List<String> parserMessages) {
        super(message);
        this.code = code;
        this.parserMessages = parserMessages == null
                ? List.of()
                : Collections.unmodifiableList(new ArrayList<>(parserMessages));
    }

    public String getCode() {
        return code;
    }

    /** Raw messages emitted by the parser for this document (diagnostics only). */
    public List<String> getParserMessages() {
        return parserMessages;
    }

    @Override
    public String toString() {
        return "SpecParseException(" + code + "): " + getMessage();
    }
}