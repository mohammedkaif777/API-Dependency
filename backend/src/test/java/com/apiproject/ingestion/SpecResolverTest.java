package com.apiproject.ingestion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SpecResolverTest {

    private static OpenAPI parse(String spec) {
        ParseOptions options = new ParseOptions();
        options.setResolve(false);
        OpenAPI openApi = new OpenAPIV3Parser().readContents(spec, null, options).getOpenAPI();
        assertNotNull(openApi, "spec must parse");
        return openApi;
    }

    private static OpenAPI resolve(String spec) {
        return new SpecResolver().resolve(parse(spec));
    }

    private static Schema<?> schemaOf(OpenAPI openApi, String path, String method) {
        Schema<?> schema = switch (method) {
            case "GET" -> openApi.getPaths().get(path).getGet()
                    .getResponses().get("200").getContent().get("application/json").getSchema();
            case "POST" -> openApi.getPaths().get(path).getPost()
                    .getRequestBody().getContent().get("application/json").getSchema();
            default -> throw new IllegalArgumentException(method);
        };
        assertNotNull(schema, "expected schema at " + path + " " + method);
        return schema;
    }

    private static final String INTERNAL_REF_SPEC = """
            openapi: 3.0.3
            info: {title: T, version: 1.0.0}
            paths:
              /pets:
                post:
                  requestBody:
                    content:
                      application/json:
                        schema: {$ref: '#/components/schemas/Pet'}
                  responses:
                    '200':
                      description: ok
                      content:
                        application/json:
                          schema:
                            type: object
                            properties:
                              pet: {$ref: '#/components/schemas/Pet'}
            components:
              schemas:
                Pet:
                  type: object
                  required: [name]
                  properties:
                    name: {type: string}
                    tag: {type: string}
            """;

    @Test
    void expandsInternalRefsAtEveryUsageSite() {
        OpenAPI openApi = resolve(INTERNAL_REF_SPEC);
        Schema<?> request = schemaOf(openApi, "/pets", "POST");

        assertNull(request.get$ref(), "requestBody schema must be dereferenced");
        assertEquals("object", request.getType());
        assertTrue(request.getProperties().containsKey("name"));
        assertTrue(request.getProperties().containsKey("tag"));
        assertEquals(java.util.List.of("name"), request.getRequired());

        Schema<?> response = openApi.getPaths().get("/pets").getPost()
                .getResponses().get("200").getContent().get("application/json").getSchema();
        Schema<?> petProp = response.getProperties().get("pet");
        assertNull(petProp.get$ref(), "deep property ref must be dereferenced");
        assertEquals("object", petProp.getType());
        assertTrue(petProp.getProperties().containsKey("name"));
        assertNoRefs(response, new HashSet<>());
    }

    private static final String ALL_OF_SPEC = """
            openapi: 3.0.3
            info: {title: T, version: 1.0.0}
            paths:
              /widgets/{id}:
                get:
                  responses:
                    '200':
                      description: ok
                      content:
                        application/json:
                          schema: {$ref: '#/components/schemas/WithTag'}
            components:
              schemas:
                Base:
                  type: object
                  required: [id]
                  properties:
                    id: {type: string}
                WithTag:
                  allOf:
                    - $ref: '#/components/schemas/Base'
                    - type: object
                      required: [tag]
                      properties:
                        tag: {type: string}
            """;

    @Test
    void mergesAllOfIntoOneEffectiveSchema() {
        Schema<?> merged = schemaOf(resolve(ALL_OF_SPEC), "/widgets/{id}", "GET");

        assertNull(merged.get$ref());
        assertNull(merged.getAllOf(), "allOf must be gone after merging");
        assertEquals("object", merged.getType());
        assertEquals(Set.of("id", "tag"), merged.getProperties().keySet());
        assertEquals(java.util.List.of("id", "tag"), merged.getRequired(), "required must be unioned");
        assertEquals("string", merged.getProperties().get("id").getType());
        assertEquals("string", merged.getProperties().get("tag").getType());
    }

    private static final String ONE_OF_SPEC = """
            openapi: 3.0.3
            info: {title: T, version: 1.0.0}
            paths:
              /x:
                post:
                  requestBody:
                    content:
                      application/json:
                        schema: {$ref: '#/components/schemas/Variant'}
                  responses:
                    '204': {description: ok}
            components:
              schemas:
                A:
                  type: object
                  properties:
                    a: {type: string}
                B:
                  type: object
                  properties:
                    b: {type: integer}
                Variant:
                  oneOf:
                    - $ref: '#/components/schemas/A'
                    - $ref: '#/components/schemas/B'
            """;

    @Test
    void leavesOneOfIntactButExpandsItsBranches() {
        Schema<?> variant = schemaOf(resolve(ONE_OF_SPEC), "/x", "POST");

        assertNotNull(variant.getOneOf(), "oneOf must be preserved, not merged (DECISION-003)");
        assertEquals(2, variant.getOneOf().size());
        assertNull(variant.getOneOf().get(0).get$ref(), "oneOf branch refs expanded");
        assertNull(variant.getOneOf().get(1).get$ref(), "oneOf branch refs expanded");
        assertTrue(variant.getOneOf().get(0).getProperties().containsKey("a"));
        assertTrue(variant.getOneOf().get(1).getProperties().containsKey("b"));
        assertNoRefs(variant, new HashSet<>());
    }

    private static final String CIRCULAR_SPEC = """
            openapi: 3.0.3
            info: {title: T, version: 1.0.0}
            paths:
              /c:
                get:
                  responses:
                    '200':
                      description: ok
                      content:
                        application/json:
                          schema: {$ref: '#/components/schemas/A'}
            components:
              schemas:
                A:
                  type: object
                  properties:
                    b: {$ref: '#/components/schemas/B'}
                B:
                  type: object
                  properties:
                    a: {$ref: '#/components/schemas/A'}
            """;

    @Test
    void rejectsMutuallyCircularRefs() {
        SpecResolutionException ex = assertThrows(
                SpecResolutionException.class, () -> resolve(CIRCULAR_SPEC));
        assertEquals(SpecResolutionException.CIRCULAR_REF, ex.getCode());
    }

    @Test
    void rejectsSelfRecursiveSchemas() {
        String spec = """
                openapi: 3.0.3
                info: {title: T, version: 1.0.0}
                paths:
                  /tree:
                    get:
                      responses:
                        '200':
                          description: ok
                          content:
                            application/json:
                              schema: {$ref: '#/components/schemas/Node'}
                components:
                  schemas:
                    Node:
                      type: object
                      properties:
                        children:
                          type: array
                          items: {$ref: '#/components/schemas/Node'}
                """;
        SpecResolutionException ex = assertThrows(
                SpecResolutionException.class, () -> resolve(spec));
        assertEquals(SpecResolutionException.CIRCULAR_REF, ex.getCode());
    }

    @Test
    void rejectsCircularComponentsEvenWhenUnreferenced() {
        String spec = """
                openapi: 3.0.3
                info: {title: T, version: 1.0.0}
                paths:
                  /z:
                    get:
                      responses:
                        '200': {description: ok}
                components:
                  schemas:
                    X:
                      type: object
                      properties:
                        y: {$ref: '#/components/schemas/Y'}
                    Y:
                      type: object
                      properties:
                        x: {$ref: '#/components/schemas/X'}
                """;
        SpecResolutionException ex = assertThrows(
                SpecResolutionException.class, () -> resolve(spec));
        assertEquals(SpecResolutionException.CIRCULAR_REF, ex.getCode());
    }

    @Test
    void rejectsExternalAndComponentLevelRefs() {
        String external = """
                openapi: 3.0.3
                info: {title: T, version: 1.0.0}
                paths:
                  /e:
                    get:
                      responses:
                        '200':
                          description: ok
                          content:
                            application/json:
                              schema: {$ref: 'https://example.com/pet.yaml#/Pet'}
                """;
        assertEquals(SpecResolutionException.UNSUPPORTED_REF,
                assertThrows(SpecResolutionException.class, () -> resolve(external)).getCode());

        String responseComponent = """
                openapi: 3.0.3
                info: {title: T, version: 1.0.0}
                paths:
                  /e:
                    get:
                      responses:
                        '200':
                          description: ok
                          content:
                            application/json:
                              schema: {$ref: '#/components/responses/NotFound'}
                components:
                  responses:
                    NotFound:
                      description: not found
                """;
        assertEquals(SpecResolutionException.UNSUPPORTED_REF,
                assertThrows(SpecResolutionException.class, () -> resolve(responseComponent)).getCode());

        String componentParameter = """
                openapi: 3.0.3
                info: {title: T, version: 1.0.0}
                paths:
                  /p:
                    get:
                      parameters:
                        - $ref: '#/components/parameters/Page'
                      responses:
                        '200': {description: ok}
                components:
                  parameters:
                    Page:
                      name: page
                      in: query
                """;
        assertEquals(SpecResolutionException.UNSUPPORTED_REF,
                assertThrows(SpecResolutionException.class, () -> resolve(componentParameter)).getCode());
    }

    @Test
    void resolvesNestedArrayAndObjectReferences() {
        String spec = """
                openapi: 3.0.3
                info: {title: T, version: 1.0.0}
                paths:
                  /list:
                    get:
                      responses:
                        '200':
                          description: ok
                          content:
                            application/json:
                              schema:
                                type: array
                                items: {$ref: '#/components/schemas/Pet'}
                components:
                  schemas:
                    Pet:
                      type: object
                      properties:
                        owner: {$ref: '#/components/schemas/Owner'}
                    Owner:
                      type: object
                      properties:
                        id: {type: string}
                """;
        OpenAPI openApi = resolve(spec);
        Schema<?> list = schemaOf(openApi, "/list", "GET");

        assertEquals("array", list.getType());
        Schema<?> item = list.getItems();
        assertNull(item.get$ref());
        assertEquals("object", item.getType());
        Schema<?> owner = item.getProperties().get("owner");
        assertNull(owner.get$ref(), "nested property ref resolved");
        assertEquals("object", owner.getType());
        assertTrue(owner.getProperties().containsKey("id"));
        assertNoRefs(list, new HashSet<>());
    }

    @Test
    void resolutionIsDeterministic() {
        assertEquals(
                new EndpointExtractor().extract(resolve(INTERNAL_REF_SPEC)),
                new EndpointExtractor().extract(resolve(INTERNAL_REF_SPEC)),
                "same input → identical resolved output (NFR-008)");
    }

    private static void assertNoRefs(Schema<?> schema, Set<String> visited) {
        if (schema == null) {
            return;
        }
        String id = System.identityHashCode(schema) + "@" + System.identityHashCode(schema.getClass());
        if (!visited.add(id)) {
            return;
        }
        assertNull(schema.get$ref(), "no $ref may survive resolution");
        if (schema.getProperties() != null) {
            schema.getProperties().values().forEach(property -> assertNoRefs(property, visited));
        }
        assertNoRefs(schema.getItems(), visited);
        if (schema.getAdditionalProperties() instanceof Schema<?> additional) {
            assertNoRefs(additional, visited);
        }
        if (schema.getAllOf() != null) {
            schema.getAllOf().forEach(part -> assertNoRefs(part, visited));
        }
        if (schema.getOneOf() != null) {
            schema.getOneOf().forEach(part -> assertNoRefs(part, visited));
        }
        if (schema.getAnyOf() != null) {
            schema.getAnyOf().forEach(part -> assertNoRefs(part, visited));
        }
        assertNoRefs(schema.getNot(), visited);
    }
}