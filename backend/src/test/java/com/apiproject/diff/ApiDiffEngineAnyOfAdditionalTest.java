package com.apiproject.diff;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.apiproject.ingestion.ExtractedEndpoint;
import com.apiproject.ingestion.ExtractedParameter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class ApiDiffEngineAnyOfAdditionalTest {

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
    void requestBodyAnyOfBranchesChangeEmitsCompositionChange() {
        ApiVersion oldVersion = new ApiVersion(ORG, SVC, "api-1",
                List.of(new ExtractedEndpoint("POST", "/users", null, List.of(),
                        json("{\"anyOf\":[{\"type\":\"string\"},{\"type\":\"object\"}]}"), null)));
        ApiVersion newVersion = new ApiVersion(ORG, SVC, "api-1",
                List.of(new ExtractedEndpoint("POST", "/users", null, List.of(),
                        json("{\"anyOf\":[{\"type\":\"string\"},{\"type\":\"array\"}]}"), null)));
        List<ApiChange> changes = ApiDiffEngine.compare(oldVersion, newVersion);
        assertTrue(changes.stream().anyMatch(c -> c.changeType() == ChangeType.SCHEMA_COMPOSITION_CHANGED
                && c.classification() == ChangeClassification.POTENTIALLY_BREAKING));
    }
}
