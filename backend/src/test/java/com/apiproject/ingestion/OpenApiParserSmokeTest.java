package com.apiproject.ingestion;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import org.junit.jupiter.api.Test;

class OpenApiParserSmokeTest {

    private static final String MINIMAL_SPEC = """
            openapi: 3.0.3
            info:
              title: Pet Store
              version: 1.0.0
            paths:
              /pets:
                get:
                  summary: List all pets
                  responses:
                    '200':
                      description: OK
              /pets/{petId}:
                put:
                  summary: Update a pet
                  parameters:
                    - name: petId
                      in: path
                      required: true
                      schema:
                        type: string
                  responses:
                    '200':
                      description: OK
            """;

    @Test
    void parsesValidOpenApi30Yaml() {
        SwaggerParseResult result =
                new OpenAPIV3Parser().readContents(MINIMAL_SPEC, null, new ParseOptions());

        OpenAPI openApi = result.getOpenAPI();
        assertNotNull(openApi, "Valid document must produce an OpenAPI model");
        assertTrue(result.getMessages().isEmpty(),
                "Parser reported issues: " + result.getMessages());

        assertNotNull(openApi.getPaths().get("/pets"), "GET /pets path must be parsed");
        assertNotNull(openApi.getPaths().get("/pets/{petId}"), "PUT /pets/{petId} path must be parsed");
        assertNotNull(openApi.getPaths().get("/pets").getGet(), "GET operation must be parsed");
        assertNotNull(openApi.getPaths().get("/pets/{petId}").getPut(), "PUT operation must be parsed");
    }
}