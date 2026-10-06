package com.apiproject.diff;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.apiproject.ingestion.ExtractedEndpoint;
import com.apiproject.ingestion.ExtractedParameter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class ApiDiffEngineTest {

    private static final String ORG = "org-1";
    private static final String SVC = "svc-1";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static JsonNode json(String s) {
        try {
            return MAPPER.readTree(s);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void identicalSnapshotsProduceNoChanges() {
        ApiVersion oldVersion = new ApiVersion(ORG, SVC, "api-1",
                List.of(new ExtractedEndpoint("GET", "/users", null, List.of(), null, null)));
        ApiVersion newVersion = new ApiVersion(ORG, SVC, "api-1",
                List.of(new ExtractedEndpoint("GET", "/users", null, List.of(), null, null)));
        List<ApiChange> changes = ApiDiffEngine.compare(oldVersion, newVersion);
        assertTrue(changes.isEmpty());
    }

    @Test
    void endpointAddedIsNonBreaking() {
        ApiVersion oldVersion = new ApiVersion(ORG, SVC, "api-1", List.of());
        ApiVersion newVersion = new ApiVersion(ORG, SVC, "api-1",
                List.of(new ExtractedEndpoint("GET", "/users", null, List.of(), null, null)));
        List<ApiChange> changes = ApiDiffEngine.compare(oldVersion, newVersion);
        assertEquals(1, changes.size());
        assertEquals(ChangeType.ENDPOINT_ADDED, changes.get(0).changeType());
        assertEquals(ChangeClassification.NON_BREAKING, changes.get(0).classification());
    }

    @Test
    void endpointRemovedIsBreaking() {
        ApiVersion oldVersion = new ApiVersion(ORG, SVC, "api-1",
                List.of(new ExtractedEndpoint("GET", "/users", null, List.of(), null, null)));
        ApiVersion newVersion = new ApiVersion(ORG, SVC, "api-1", List.of());
        List<ApiChange> changes = ApiDiffEngine.compare(oldVersion, newVersion);
        assertEquals(1, changes.size());
        assertEquals(ChangeType.ENDPOINT_REMOVED, changes.get(0).changeType());
        assertEquals(ChangeClassification.BREAKING, changes.get(0).classification());
    }

    @Test
    void methodAddedOnSamePathIsNonBreaking() {
        ApiVersion oldVersion = new ApiVersion(ORG, SVC, "api-1",
                List.of(new ExtractedEndpoint("GET", "/users", null, List.of(), null, null)));
        ApiVersion newVersion = new ApiVersion(ORG, SVC, "api-1",
                List.of(
                        new ExtractedEndpoint("GET", "/users", null, List.of(), null, null),
                        new ExtractedEndpoint("POST", "/users", null, List.of(), null, null)));
        List<ApiChange> changes = ApiDiffEngine.compare(oldVersion, newVersion);
        assertEquals(1, changes.size());
        assertEquals(ChangeType.ENDPOINT_ADDED, changes.get(0).changeType());
        assertEquals("POST", changes.get(0).method());
        assertEquals("/users", changes.get(0).affectedPath());
        assertEquals(ChangeClassification.NON_BREAKING, changes.get(0).classification());
    }

    @Test
    void methodRemovedOnSamePathIsBreaking() {
        ApiVersion oldVersion = new ApiVersion(ORG, SVC, "api-1",
                List.of(
                        new ExtractedEndpoint("GET", "/users", null, List.of(), null, null),
                        new ExtractedEndpoint("POST", "/users", null, List.of(), null, null)));
        ApiVersion newVersion = new ApiVersion(ORG, SVC, "api-1",
                List.of(new ExtractedEndpoint("GET", "/users", null, List.of(), null, null)));
        List<ApiChange> changes = ApiDiffEngine.compare(oldVersion, newVersion);
        assertEquals(1, changes.size());
        assertEquals(ChangeType.ENDPOINT_REMOVED, changes.get(0).changeType());
        assertEquals("POST", changes.get(0).method());
        assertEquals("/users", changes.get(0).affectedPath());
        assertEquals(ChangeClassification.BREAKING, changes.get(0).classification());
    }

    @Test
    void requiredParamAddedIsBreaking() {
        ApiVersion oldVersion = new ApiVersion(ORG, SVC, "api-1",
                List.of(new ExtractedEndpoint("GET", "/users", null, List.of(), null, null)));
        ApiVersion newVersion = new ApiVersion(ORG, SVC, "api-1",
                List.of(new ExtractedEndpoint("GET", "/users", null,
                        List.of(new ExtractedParameter("id", "query", true, json("{\"type\":\"string\"}"))),
                        null, null)));
        List<ApiChange> changes = ApiDiffEngine.compare(oldVersion, newVersion);
        assertTrue(changes.stream().anyMatch(c -> c.changeType() == ChangeType.PARAM_ADDED
                && c.classification() == ChangeClassification.BREAKING));
    }

    @Test
    void optionalParamAddedIsNonBreaking() {
        ApiVersion oldVersion = new ApiVersion(ORG, SVC, "api-1",
                List.of(new ExtractedEndpoint("GET", "/users", null, List.of(), null, null)));
        ApiVersion newVersion = new ApiVersion(ORG, SVC, "api-1",
                List.of(new ExtractedEndpoint("GET", "/users", null,
                        List.of(new ExtractedParameter("limit", "query", false, json("{\"type\":\"integer\"}"))),
                        null, null)));
        List<ApiChange> changes = ApiDiffEngine.compare(oldVersion, newVersion);
        assertTrue(changes.stream().anyMatch(c -> c.changeType() == ChangeType.PARAM_ADDED
                && c.classification() == ChangeClassification.NON_BREAKING));
    }

    @Test
    void paramRemovedIsBreaking() {
        ApiVersion oldVersion = new ApiVersion(ORG, SVC, "api-1",
                List.of(new ExtractedEndpoint("GET", "/users", null,
                        List.of(new ExtractedParameter("id", "query", true, json("{\"type\":\"string\"}"))),
                        null, null)));
        ApiVersion newVersion = new ApiVersion(ORG, SVC, "api-1",
                List.of(new ExtractedEndpoint("GET", "/users", null, List.of(), null, null)));
        List<ApiChange> changes = ApiDiffEngine.compare(oldVersion, newVersion);
        assertTrue(changes.stream().anyMatch(c -> c.changeType() == ChangeType.PARAM_REMOVED
                && c.classification() == ChangeClassification.BREAKING));
    }

    @Test
    void paramRequiredTrueToFalseIsNonBreaking() {
        ApiVersion oldVersion = new ApiVersion(ORG, SVC, "api-1",
                List.of(new ExtractedEndpoint("GET", "/users", null,
                        List.of(new ExtractedParameter("id", "query", true, json("{\"type\":\"string\"}"))),
                        null, null)));
        ApiVersion newVersion = new ApiVersion(ORG, SVC, "api-1",
                List.of(new ExtractedEndpoint("GET", "/users", null,
                        List.of(new ExtractedParameter("id", "query", false, json("{\"type\":\"string\"}"))),
                        null, null)));
        List<ApiChange> changes = ApiDiffEngine.compare(oldVersion, newVersion);
        assertTrue(changes.stream().anyMatch(c -> c.changeType() == ChangeType.PARAM_REQUIRED_CHANGED
                && c.classification() == ChangeClassification.NON_BREAKING));
    }

    @Test
    void paramRequiredFalseToTrueIsBreaking() {
        ApiVersion oldVersion = new ApiVersion(ORG, SVC, "api-1",
                List.of(new ExtractedEndpoint("GET", "/users", null,
                        List.of(new ExtractedParameter("id", "query", false, json("{\"type\":\"string\"}"))),
                        null, null)));
        ApiVersion newVersion = new ApiVersion(ORG, SVC, "api-1",
                List.of(new ExtractedEndpoint("GET", "/users", null,
                        List.of(new ExtractedParameter("id", "query", true, json("{\"type\":\"string\"}"))),
                        null, null)));
        List<ApiChange> changes = ApiDiffEngine.compare(oldVersion, newVersion);
        assertTrue(changes.stream().anyMatch(c -> c.changeType() == ChangeType.PARAM_REQUIRED_CHANGED
                && c.classification() == ChangeClassification.BREAKING));
    }

    @Test
    void paramTypeChangedIsPotentiallyBreaking() {
        ApiVersion oldVersion = new ApiVersion(ORG, SVC, "api-1",
                List.of(new ExtractedEndpoint("GET", "/users", null,
                        List.of(new ExtractedParameter("id", "query", true, json("{\"type\":\"string\"}"))),
                        null, null)));
        ApiVersion newVersion = new ApiVersion(ORG, SVC, "api-1",
                List.of(new ExtractedEndpoint("GET", "/users", null,
                        List.of(new ExtractedParameter("id", "query", true, json("{\"type\":\"integer\"}"))),
                        null, null)));
        List<ApiChange> changes = ApiDiffEngine.compare(oldVersion, newVersion);
        assertTrue(changes.stream().anyMatch(c -> c.changeType() == ChangeType.PARAM_TYPE_CHANGED
                && c.classification() == ChangeClassification.POTENTIALLY_BREAKING));
    }

    @Test
    void requestBodySchemaChangedIsPotentiallyBreaking() {
        ApiVersion oldVersion = new ApiVersion(ORG, SVC, "api-1",
                List.of(new ExtractedEndpoint("POST", "/users", null, List.of(),
                        json("{\"type\":\"object\",\"properties\":{\"name\":{\"type\":\"string\"}}}"),
                        null)));
        ApiVersion newVersion = new ApiVersion(ORG, SVC, "api-1",
                List.of(new ExtractedEndpoint("POST", "/users", null, List.of(),
                        json("{\"type\":\"object\",\"properties\":{\"name\":{\"type\":\"string\"},\"age\":{\"type\":\"integer\"}}}"),
                        null)));
        List<ApiChange> changes = ApiDiffEngine.compare(oldVersion, newVersion);
        assertTrue(changes.stream().anyMatch(c -> c.changeType() == ChangeType.REQUEST_BODY_CHANGED
                && c.classification() == ChangeClassification.POTENTIALLY_BREAKING));
    }

    @Test
    void responseSchemaChangedIsPotentiallyBreaking() {
        ApiVersion oldVersion = new ApiVersion(ORG, SVC, "api-1",
                List.of(new ExtractedEndpoint("GET", "/users", null, List.of(), null,
                        json("{\"type\":\"array\",\"items\":{\"type\":\"string\"}}"))));
        ApiVersion newVersion = new ApiVersion(ORG, SVC, "api-1",
                List.of(new ExtractedEndpoint("GET", "/users", null, List.of(), null,
                        json("{\"type\":\"array\",\"items\":{\"type\":\"object\"}}"))));
        List<ApiChange> changes = ApiDiffEngine.compare(oldVersion, newVersion);
        assertTrue(changes.stream().anyMatch(c -> c.changeType() == ChangeType.RESPONSE_CHANGED
                && c.classification() == ChangeClassification.POTENTIALLY_BREAKING));
    }

    @Test
    void oneOfPresenceTriggersSchemaCompositionChanged() {
        ApiVersion oldVersion = new ApiVersion(ORG, SVC, "api-1",
                List.of(new ExtractedEndpoint("POST", "/users", null, List.of(),
                        json("{\"type\":\"object\"}"),
                        null)));
        ApiVersion newVersion = new ApiVersion(ORG, SVC, "api-1",
                List.of(new ExtractedEndpoint("POST", "/users", null, List.of(),
                        json("{\"oneOf\":[{\"type\":\"object\"},{\"type\":\"string\"}]}"),
                        null)));
        List<ApiChange> changes = ApiDiffEngine.compare(oldVersion, newVersion);
        assertTrue(changes.stream().anyMatch(c -> c.changeType() == ChangeType.SCHEMA_COMPOSITION_CHANGED
                && c.classification() == ChangeClassification.POTENTIALLY_BREAKING));
    }

    @Test
    void paramOrderDoesNotAffectDiff() {
        ApiVersion oldVersion = new ApiVersion(ORG, SVC, "api-1",
                List.of(new ExtractedEndpoint("GET", "/users", null,
                        List.of(
                                new ExtractedParameter("a", "query", false, json("{\"type\":\"string\"}")),
                                new ExtractedParameter("b", "query", false, json("{\"type\":\"string\"}"))),
                        null, null)));
        ApiVersion newVersion = new ApiVersion(ORG, SVC, "api-1",
                List.of(new ExtractedEndpoint("GET", "/users", null,
                        List.of(
                                new ExtractedParameter("b", "query", false, json("{\"type\":\"string\"}")),
                                new ExtractedParameter("a", "query", false, json("{\"type\":\"string\"}"))),
                        null, null)));
        List<ApiChange> changes = ApiDiffEngine.compare(oldVersion, newVersion);
        assertTrue(changes.isEmpty());
    }

    @Test
    void identicalWithDifferentParamOrderProducesNoChanges() {
        ApiVersion oldVersion = new ApiVersion(ORG, SVC, "api-1",
                List.of(new ExtractedEndpoint("GET", "/search", null,
                        List.of(
                                new ExtractedParameter("q", "query", true, json("{\"type\":\"string\"}")),
                                new ExtractedParameter("limit", "query", false, json("{\"type\":\"integer\"}"))),
                        null, null)));
        ApiVersion newVersion = new ApiVersion(ORG, SVC, "api-1",
                List.of(new ExtractedEndpoint("GET", "/search", null,
                        List.of(
                                new ExtractedParameter("limit", "query", false, json("{\"type\":\"integer\"}")),
                                new ExtractedParameter("q", "query", true, json("{\"type\":\"string\"}"))),
                        null, null)));
        List<ApiChange> changes = ApiDiffEngine.compare(oldVersion, newVersion);
        assertTrue(changes.isEmpty());
    }
}
