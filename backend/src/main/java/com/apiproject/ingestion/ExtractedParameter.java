package com.apiproject.ingestion;

import com.fasterxml.jackson.databind.JsonNode;

public record ExtractedParameter(String name, String in, boolean required, JsonNode schema) {
}