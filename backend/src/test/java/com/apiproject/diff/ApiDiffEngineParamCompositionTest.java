package com.apiproject.diff;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.apiproject.ingestion.ExtractedEndpoint;
import com.apiproject.ingestion.ExtractedParameter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class ApiDiffEngineParamCompositionTest {

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
    void paramCompositionTriggersPotentiallyBreaking() {
        ApiVersion oldVersion = new ApiVersion(ORG, SVC, "api-1",
                List.of(new ExtractedEndpoint("GET", "/users", null,
                        List.of(new ExtractedParameter("filter", "query", false, json("{\"type\":\"string\"}"))),
                        null, null)));
        ApiVersion newVersion = new ApiVersion(ORG, SVC, "api-1",
                List.of(new ExtractedEndpoint("GET", "/users", null,
                        List.of(new ExtractedParameter("filter", "query", false,
                                json("{\"anyOf\":[{\"type\":\"string\"},{\"type\":\"integer\"}]}"))),
                        null, null)));
        List<ApiChange> changes = ApiDiffEngine.compare(oldVersion, newVersion);
        assertTrue(changes.stream().anyMatch(c -> c.changeType() == ChangeType.SCHEMA_COMPOSITION_CHANGED
                && c.classification() == ChangeClassification.POTENTIALLY_BREAKING));
    }

    @Test
    void responseCompositionTriggersPotentiallyBreaking() {
        ApiVersion oldVersion = new ApiVersion(ORG, SVC, "api-1",
                List.of(new ExtractedEndpoint("GET", "/users", null, List.of(), null, json("{\"type\":\"object\"}"))));
        ApiVersion newVersion = new ApiVersion(ORG, SVC, "api-1",
                List.of(new ExtractedEndpoint("GET", "/users", null, List.of(), null,
                        json("{\"oneOf\":[{\"type\":\"object\"},{\"type\":\"array\"}]}"))));
        List<ApiChange> changes = ApiDiffEngine.compare(oldVersion, newVersion);
        assertTrue(changes.stream().anyMatch(c -> c.changeType() == ChangeType.SCHEMA_COMPOSITION_CHANGED
                && c.classification() == ChangeClassification.POTENTIALLY_BREAKING));
    }
}
