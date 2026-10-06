package com.apiproject.ingestion;

import io.swagger.v3.oas.models.OpenAPI;
import java.util.Collections;
import java.util.List;

/**
 * Result of a successful parse: the parsed but unresolved model plus any non-fatal warnings the
 * parser reported. Ingestion does not fail on warnings (e.g. an undeclared path parameter); they are
 * surfaced for the API layer later. The model is unresolved — run {@link SpecResolver} before any
 * consumer that must not see {@code $ref}s.
 */
public final class SpecParseResult {

    private final OpenAPI openApi;
    private final List<String> warnings;

    public SpecParseResult(OpenAPI openApi, List<String> warnings) {
        this.openApi = openApi;
        this.warnings = warnings == null ? List.of() : Collections.unmodifiableList(warnings);
    }

    public OpenAPI getOpenApi() {
        return openApi;
    }

    public List<String> getWarnings() {
        return warnings;
    }
}