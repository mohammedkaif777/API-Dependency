package com.apiproject.diff;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.apiproject.ingestion.ExtractedEndpoint;
import com.apiproject.ingestion.ExtractedParameter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class ApiDiffEngineCompositionTest {

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
    void oneOfAppearsEmitsCompositionChange() {
        ApiVersion oldVersion = new ApiVersion(ORG, SVC, "api-1",
                List.of(new ExtractedEndpoint("POST", "/users", null, List.of(), json("{\"type\":\"object\"}"), null)));
        ApiVersion newVersion = new ApiVersion(ORG, SVC, "api-1",
                List.of(new ExtractedEndpoint("POST", "/users", null, List.of(),
                        json("{\"oneOf\":[{\"type\":\"object\"},{\"type\":\"string\"}]}"), null)));
        List<ApiChange> changes = ApiDiffEngine.compare(oldVersion, newVersion);
        assertTrue(changes.stream().anyMatch(c -> c.changeType() == ChangeType.SCHEMA_COMPOSITION_CHANGED
                && c.classification() == ChangeClassification.POTENTIALLY_BREAKING));
    }

    @Test
    void oneOfDisappearsEmitsCompositionChange() {
        ApiVersion oldVersion = new ApiVersion(ORG, SVC, "api-1",
                List.of(new ExtractedEndpoint("POST", "/users", null, List.of(),
                        json("{\"oneOf\":[{\"type\":\"object\"},{\"type\":\"string\"}]}"), null)));
        ApiVersion newVersion = new ApiVersion(ORG, SVC, "api-1",
                List.of(new ExtractedEndpoint("POST", "/users", null, List.of(), json("{\"type\":\"object\"}"), null)));
        List<ApiChange> changes = ApiDiffEngine.compare(oldVersion, newVersion);
        assertTrue(changes.stream().anyMatch(c -> c.changeType() == ChangeType.SCHEMA_COMPOSITION_CHANGED
                && c.classification() == ChangeClassification.POTENTIALLY_BREAKING));
    }

    @Test
    void anyOfAppearsEmitsCompositionChange() {
        ApiVersion oldVersion = new ApiVersion(ORG, SVC, "api-1",
                List.of(new ExtractedEndpoint("GET", "/users", null, List.of(), null, json("{\"type\":\"object\"}"))));
        ApiVersion newVersion = new ApiVersion(ORG, SVC, "api-1",
                List.of(new ExtractedEndpoint("GET", "/users", null, List.of(), null,
                        json("{\"anyOf\":[{\"type\":\"object\"},{\"type\":\"array\"}]}"))));
        List<ApiChange> changes = ApiDiffEngine.compare(oldVersion, newVersion);
        assertTrue(changes.stream().anyMatch(c -> c.changeType() == ChangeType.SCHEMA_COMPOSITION_CHANGED
                && c.classification() == ChangeClassification.POTENTIALLY_BREAKING));
    }

    @Test
    void anyOfDisappearsEmitsCompositionChange() {
        ApiVersion oldVersion = new ApiVersion(ORG, SVC, "api-1",
                List.of(new ExtractedEndpoint("GET", "/users", null, List.of(), null,
                        json("{\"anyOf\":[{\"type\":\"object\"},{\"type\":\"array\"}]}"))));
        ApiVersion newVersion = new ApiVersion(ORG, SVC, "api-1",
                List.of(new ExtractedEndpoint("GET", "/users", null, List.of(), null, json("{\"type\":\"object\"}"))));
        List<ApiChange> changes = ApiDiffEngine.compare(oldVersion, newVersion);
        assertTrue(changes.stream().anyMatch(c -> c.changeType() == ChangeType.SCHEMA_COMPOSITION_CHANGED
                && c.classification() == ChangeClassification.POTENTIALLY_BREAKING));
    }

    @Test
    void compositionRemainsButBranchesChangeEmitsCompositionChange() {
        ApiVersion oldVersion = new ApiVersion(ORG, SVC, "api-1",
                List.of(new ExtractedEndpoint("POST", "/users", null, List.of(),
                        json("{\"oneOf\":[{\"type\":\"object\"},{\"type\":\"string\"}]}"), null)));
        ApiVersion newVersion = new ApiVersion(ORG, SVC, "api-1",
                List.of(new ExtractedEndpoint("POST", "/users", null, List.of(),
                        json("{\"oneOf\":[{\"type\":\"object\"},{\"type\":\"number\"}]}"), null)));
        List<ApiChange> changes = ApiDiffEngine.compare(oldVersion, newVersion);
        assertTrue(changes.stream().anyMatch(c -> c.changeType() == ChangeType.SCHEMA_COMPOSITION_CHANGED
                && c.classification() == ChangeClassification.POTENTIALLY_BREAKING));
    }

    @Test
    void paramHasOneOfInBothHandledConservatively() {
        ApiVersion oldVersion = new ApiVersion(ORG, SVC, "api-1",
                List.of(new ExtractedEndpoint("GET", "/users", null,
                        List.of(new ExtractedParameter("filter", "query", false,
                                json("{\"oneOf\":[{\"type\":\"string\"},{\"type\":\"integer\"}]}"))),
                        null, null)));
        ApiVersion newVersion = new ApiVersion(ORG, SVC, "api-1",
                List.of(new ExtractedEndpoint("GET", "/users", null,
                        List.of(new ExtractedParameter("filter", "query", false,
                                json("{\"oneOf\":[{\"type\":\"string\"},{\"type\":\"number\"}]}"))),
                        null, null)));
        List<ApiChange> changes = ApiDiffEngine.compare(oldVersion, newVersion);
        assertTrue(changes.stream().anyMatch(c -> c.changeType() == ChangeType.SCHEMA_COMPOSITION_CHANGED
                && c.classification() == ChangeClassification.POTENTIALLY_BREAKING));
    }
}
