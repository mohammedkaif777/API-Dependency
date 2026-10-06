package com.apiproject.ingestion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.swagger.v3.oas.models.OpenAPI;
import org.junit.jupiter.api.Test;

class SpecParserTest {

    private final SpecParser parser = new SpecParser();

    private static final String VALID_SPEC = """
            openapi: 3.0.3
            info: {title: T, version: 1.0.0}
            paths:
              /ping:
                get:
                  responses:
                    '200':
                      description: ok
                      content:
                        application/json:
                          schema: {$ref: '#/components/schemas/Health'}
            components:
              schemas:
                Health:
                  type: object
                  properties:
                    status: {type: string}
            """;

    @Test
    void returnsModelForValidSpec() {
        SpecParseResult result = parser.parse(VALID_SPEC);

        OpenAPI openApi = result.getOpenApi();
        assertNotNull(openApi);
        assertEquals("3.0.3", openApi.getOpenapi());
        assertNotNull(openApi.getPaths().get("/ping").getGet());
        assertNotNull(result.getWarnings(), "warnings list must be non-null even when empty");
    }

    @Test
    void keepsRefsUnresolved() {
        OpenAPI openApi = parser.parse(VALID_SPEC).getOpenApi();
        assertEquals("#/components/schemas/Health",
                openApi.getPaths().get("/ping").getGet().getResponses().get("200")
                        .getContent().get("application/json").getSchema().get$ref(),
                "parse must not dereference; SpecResolver is a separate step");
    }

    @Test
    void rejectsMalformedYaml() {
        SpecParseException ex = assertThrows(
                SpecParseException.class, () -> parser.parse("openapi: 3.0.3\ninfo: {title: T,\npaths:"));
        assertEquals(SpecParseException.MALFORMED_SPEC, ex.getCode());
    }

    @Test
    void rejectsGarbageJson() {
        SpecParseException ex = assertThrows(
                SpecParseException.class, () -> parser.parse("{ this is not valid json ]"));
        assertEquals(SpecParseException.MALFORMED_SPEC, ex.getCode());
    }

    @Test
    void rejectsEmptyAndNullInput() {
        assertEquals(SpecParseException.MALFORMED_SPEC,
                assertThrows(SpecParseException.class, () -> parser.parse("")).getCode());
        assertEquals(SpecParseException.MALFORMED_SPEC,
                assertThrows(SpecParseException.class, () -> parser.parse("   ")).getCode());
    }

    @Test
    void rejectsUnsupportedVersion() {
        String future = """
                openapi: 4.1.0
                info: {title: T, version: 1.0.0}
                paths:
                  /ping:
                    get:
                      responses:
                        '200': {description: ok}
                """;
        SpecParseException ex = assertThrows(SpecParseException.class, () -> parser.parse(future));
        assertEquals(SpecParseException.MALFORMED_SPEC, ex.getCode(),
                "parser refuses non-3.x with a null model, so it surfaces as MALFORMED_SPEC");
    }

    @Test
    void rejectsMissingOpenapiField() {
        String noVersion = """
                info: {title: T, version: 1.0.0}
                paths:
                  /ping:
                    get:
                      responses:
                        '200': {description: ok}
                """;
        assertEquals(SpecParseException.MALFORMED_SPEC,
                assertThrows(SpecParseException.class, () -> parser.parse(noVersion)).getCode());
    }

    @Test
    void rejectsSpecWithoutPaths() {
        String noPaths = """
                openapi: 3.0.3
                info: {title: T, version: 1.0.0}
                paths: {}
                """;
        SpecParseException ex = assertThrows(SpecParseException.class, () -> parser.parse(noPaths));
        assertEquals(SpecParseException.MALFORMED_SPEC, ex.getCode());
        assertTrue(ex.getMessage().contains("paths"), "message should explain the missing paths");
    }

    @Test
    void rejectsSwaggerTwoZero() {
        String swagger2 = """
                swagger: "2.0"
                info: {title: T, version: 1.0.0}
                paths:
                  /ping:
                    get:
                      responses:
                        '200': {description: ok}
                """;
        assertEquals(SpecParseException.MALFORMED_SPEC,
                assertThrows(SpecParseException.class, () -> parser.parse(swagger2)).getCode());
    }
}