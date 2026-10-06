package com.apiproject.ingestion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import java.util.List;
import org.junit.jupiter.api.Test;

class EndpointExtractorTest {

    private static final String SPEC = """
            openapi: 3.0.3
            info:
              title: Pets
              version: 1.0.0
            paths:
              /pets:
                get:
                  summary: List pets
                  parameters:
                    - name: limit
                      in: query
                      schema:
                        type: integer
                    - name: X-Trace
                      in: header
                      schema:
                        type: string
                  responses:
                    '200':
                      description: OK
                      content:
                        application/json:
                          schema:
                            type: array
                            items:
                              $ref: '#/components/schemas/Pet'
                    '404':
                      description: not allowed here
                post:
                  summary: Create pet
                  requestBody:
                    content:
                      application/json:
                        schema:
                          type: object
                          required: [name]
                          properties:
                            name:
                              type: string
                            tag:
                              type: string
                  responses:
                    '201':
                      description: created
                      content:
                        application/json:
                          schema:
                            type: object
                            properties:
                              id:
                                type: string
              /pets/{petId}:
                parameters:
                  - name: petId
                    in: path
                    required: true
                    schema:
                      type: string
                get:
                  summary: Get one pet
                  responses:
                    '200':
                      description: OK
                      content:
                        application/json:
                          schema:
                            $ref: '#/components/schemas/Pet'
                delete:
                  summary: Delete pet
                  responses:
                    '204':
                      description: no content
            components:
              schemas:
                Pet:
                  type: object
                  properties:
                    id:
                      type: string
                    name:
                      type: string
            """;

    private static List<ExtractedEndpoint> extract(String spec) {
        OpenAPI openApi = new OpenAPIV3Parser().readContents(spec, null, new ParseOptions()).getOpenAPI();
        assertNotNull(openApi, "spec must parse");
        return new EndpointExtractor().extract(openApi);
    }

    @Test
    void extractsAllSupportedMethodsDeterministically() {
        List<ExtractedEndpoint> endpoints = extract(SPEC);

        assertEquals(
                List.of("/pets", "GET"),
                List.of(endpoints.get(0).path(), endpoints.get(0).method()),
                "first item must be GET /pets in sorted order");
        assertTrue(endpoints.get(1).method().contentEquals("POST"));
        assertEquals("/pets/{petId}", endpoints.get(2).path());
        assertTrue(endpoints.get(2).method().contentEquals("DELETE"));
        assertTrue(endpoints.get(3).method().contentEquals("GET"));

        assertEquals(4, endpoints.size());
        for (ExtractedEndpoint endpoint : endpoints) {
            assertTrue(List.of("GET", "POST", "PUT", "DELETE", "PATCH").contains(endpoint.method()),
                    "method must be within INV-EP-02 set");
            assertTrue(endpoint.path().startsWith("/"), "path must keep leading slash (INV-EP-03)");
            assertFalse(endpoint.path().contains(":"), "{param} form preserved, not ':param'");
        }
    }

    @Test
    void extractsParametersAcrossLocations() {
        List<ExtractedEndpoint> endpoints = extract(SPEC);

        ExtractedEndpoint listPets = endpoints.get(0);
        assertEquals(List.of("limit", "X-Trace"),
                listPets.parameters().stream().map(ExtractedParameter::name).toList());
        assertFalse(listPets.parameters().get(0).required());
        assertEquals("query", listPets.parameters().get(0).in());

        ExtractedEndpoint getOne = endpoints.get(3);
        assertEquals(1, getOne.parameters().size());
        assertEquals("petId", getOne.parameters().get(0).name());
        assertEquals("path", getOne.parameters().get(0).in());
        assertTrue(getOne.parameters().get(0).required(), "petId is required");
    }

    @Test
    void extractsRequestBodyAndSuccessResponseSchemas() {
        ExtractedEndpoint createPet = extract(SPEC).get(1);

        JsonNode body = createPet.requestBodySchema();
        assertNotNull(body, "POST /pets must have a request body schema");
        assertEquals("object", body.path("type").asText());
        assertEquals("string", body.path("properties").path("name").path("type").asText());
        assertTrue(body.path("required").isArray(), "required list preserved");

        JsonNode response = createPet.responseSchema();
        assertNotNull(response);
        assertEquals("object", response.path("type").asText());
        assertEquals("string", response.path("properties").path("id").path("type").asText(),
                "201 must win over any non-2xx response");
    }

    @Test
    void missingBodyAndNoContentResponseYieldNullSchemas() {
        ExtractedEndpoint deletePet = extract(SPEC).get(2);

        assertNull(deletePet.requestBodySchema(), "no request body on DELETE");
        assertNull(deletePet.responseSchema(), "204 has no content → null, not a failure");
    }

    @Test
    void requestBodyAndResponsesAreOptional() {
        String minimal = """
                openapi: 3.0.3
                info:
                  title: Ping
                  version: 1.0.0
                paths:
                  /ping:
                    get:
                      summary: Ping
                """;
        ExtractedEndpoint ping = extract(minimal).get(0);

        assertNotNull(ping);
        assertNull(ping.requestBodySchema());
        assertNull(ping.responseSchema());
    }

    @Test
    void unresolvedRefSchemaIsCapturedNotDropped() {
        ExtractedEndpoint getOne = extract(SPEC).get(3);
        assertNotNull(getOne.responseSchema(), "$ref schema captured as-is; resolution is P3-03 (DECISION-003)");
    }

    @Test
    void sameInputYieldsIdenticalOutput() {
        assertEquals(extract(SPEC), extract(SPEC), "deterministic: NFR-008");
    }
}