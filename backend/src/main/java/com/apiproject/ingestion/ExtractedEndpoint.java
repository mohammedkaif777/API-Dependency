package com.apiproject.ingestion;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

public record ExtractedEndpoint(
        String method,
        String path,
        String summary,
        List<ExtractedParameter> parameters,
        JsonNode requestBodySchema,
        JsonNode responseSchema) {
}