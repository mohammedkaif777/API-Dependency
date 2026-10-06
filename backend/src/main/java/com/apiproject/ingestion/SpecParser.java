package com.apiproject.ingestion;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import java.util.List;

/**
 * Stable ingestion entry point for an OpenAPI document (FR-003). Parsing the YAML/JSON is delegated
 * to the OpenAPI library (P3-01); this class is only a thin failing-on-fatal-error wrapper so every
 * failure can be reasoned about as a typed error:
 * <ul>
 *   <li>syntax/structural breakage or zero paths → {@link SpecParseException#MALFORMED_SPEC},</li>
 *   <li>{@code openapi} present but not 3.x → {@link SpecParseException#UNSUPPORTED_SPEC_VERSION},</li>
 *   <li>{@code openapi} missing entirely → {@link SpecParseException#MALFORMED_SPEC},</li>
 *   <li>non-fatal parser messages → carried as warnings, never fatal.</li>
 * </ul>
 *
 * <p>Pure and deterministic (NFR-008): no IO, no network, same content → same result. The returned
 * model is unresolved; run {@link SpecResolver} before any consumer that must not see {@code $ref}s.
 * HTTP status mapping (400) is the API layer's job (later phase).
 *
 * <p>Note: the OpenAPI library refuses non-3.x documents (Swagger 2.0, hypothetical 4.x) by
 * returning a null model, so they surface as {@code MALFORMED_SPEC} rather than a distinct
 * "unsupported version" code — deliberately no dead taxonomy (the sequence-diagram contract of
 * {@code 400 { code: ... }} is still unchanged).</p>
 */
public final class SpecParser {

    public SpecParseResult parse(String content) {
        SwaggerParseResult result;
        try {
            ParseOptions options = new ParseOptions();
            options.setResolve(false);
            result = new OpenAPIV3Parser().readContents(content, null, options);
        } catch (RuntimeException e) {
            throw new SpecParseException(
                    SpecParseException.MALFORMED_SPEC,
                    "spec could not be parsed: " + e.getMessage(),
                    List.of(String.valueOf(e.getMessage())));
        }
        if (result == null) {
            throw new SpecParseException(
                    SpecParseException.MALFORMED_SPEC,
                    "spec could not be parsed",
                    List.of());
        }
        OpenAPI openApi = result.getOpenAPI();
        if (openApi == null) {
            throw new SpecParseException(
                    SpecParseException.MALFORMED_SPEC,
                    "spec could not be parsed (expected OpenAPI 3.x):\n" + formatted(result.getMessages()),
                    result.getMessages());
        }
        if (openApi.getPaths() == null || openApi.getPaths().isEmpty()) {
            throw new SpecParseException(
                    SpecParseException.MALFORMED_SPEC,
                    "spec has no paths to ingest",
                    result.getMessages());
        }
        return new SpecParseResult(openApi, result.getMessages() == null ? List.of() : result.getMessages());
    }

    private static String formatted(java.util.List<String> messages) {
        if (messages == null || messages.isEmpty()) {
            return "(no parser diagnostics)";
        }
        return String.join("\n", messages);
    }
}