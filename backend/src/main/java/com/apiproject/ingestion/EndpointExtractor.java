package com.apiproject.ingestion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure extraction: parsed OpenAPI model -> ExtractedEndpoint values.
 * No I/O, no persistence, no dependency detection (NFR-008).
 *
 * <p>Schemas are captured as-is; $ref / allOf resolution is P3-03 (DECISION-003), so a
 * captured schema may still contain an unresolved $ref — deferred, not dropped.</p>
 */
public final class EndpointExtractor {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String PREFERRED_CONTENT_TYPE = "application/json";

    /** INV-EP-02 allowed methods, fixed order so output is deterministic.
     *  DECISION: HEAD/OPTIONS/TRACE are skipped — outside INV-EP-02's set. */
    private static final List<PathItem.HttpMethod> SUPPORTED_METHODS =
            List.of(
                    PathItem.HttpMethod.GET,
                    PathItem.HttpMethod.PUT,
                    PathItem.HttpMethod.POST,
                    PathItem.HttpMethod.DELETE,
                    PathItem.HttpMethod.PATCH);

    public List<ExtractedEndpoint> extract(OpenAPI openApi) {
        List<ExtractedEndpoint> endpoints = new ArrayList<>();
        if (openApi == null || openApi.getPaths() == null) {
            return endpoints;
        }
        for (Map.Entry<String, PathItem> entry : openApi.getPaths().entrySet()) {
            String path = entry.getKey();
            PathItem pathItem = entry.getValue();
            List<ExtractedParameter> pathLevel = extractParameters(pathItem.getParameters());
            for (PathItem.HttpMethod method : SUPPORTED_METHODS) {
                Operation operation = operationOf(pathItem, method);
                if (operation == null) {
                    continue;
                }
                List<ExtractedParameter> operationLevel = extractParameters(operation.getParameters());
                endpoints.add(
                        new ExtractedEndpoint(
                                method.name(),
                                path,
                                operation.getSummary(),
                                mergeParameters(pathLevel, operationLevel),
                                schemaOf(operation.getRequestBody()),
                                schemaOfSuccessResponse(operation.getResponses())));
            }
        }
        endpoints.sort(
                Comparator.comparing(ExtractedEndpoint::path).thenComparing(ExtractedEndpoint::method));
        return endpoints;
    }

    private Operation operationOf(PathItem pathItem, PathItem.HttpMethod method) {
        return switch (method) {
            case GET -> pathItem.getGet();
            case PUT -> pathItem.getPut();
            case POST -> pathItem.getPost();
            case DELETE -> pathItem.getDelete();
            case PATCH -> pathItem.getPatch();
            default -> null;
        };
    }

    /** Operation-level parameters override path-level ones with the same (name, in) — spec rule. */
    private List<ExtractedParameter> mergeParameters(
            List<ExtractedParameter> pathLevel, List<ExtractedParameter> operationLevel) {
        Map<String, ExtractedParameter> merged = new LinkedHashMap<>();
        for (ExtractedParameter parameter : pathLevel) {
            merged.put(key(parameter), parameter);
        }
        for (ExtractedParameter parameter : operationLevel) {
            merged.put(key(parameter), parameter);
        }
        return new ArrayList<>(merged.values());
    }

    private String key(ExtractedParameter parameter) {
        return parameter.name() + "|" + parameter.in();
    }

    private List<ExtractedParameter> extractParameters(List<Parameter> parameters) {
        List<ExtractedParameter> extracted = new ArrayList<>();
        if (parameters == null) {
            return extracted;
        }
        for (Parameter parameter : parameters) {
            extracted.add(
                    new ExtractedParameter(
                            parameter.getName(),
                            parameter.getIn(),
                            Boolean.TRUE.equals(parameter.getRequired()),
                            toJson(parameter.getSchema())));
        }
        return extracted;
    }

    private JsonNode schemaOf(io.swagger.v3.oas.models.parameters.RequestBody requestBody) {
        if (requestBody == null) {
            return null;
        }
        MediaType mediaType = pickMediaType(requestBody.getContent());
        return mediaType == null ? null : toJson(mediaType.getSchema());
    }

    private JsonNode schemaOfSuccessResponse(ApiResponses responses) {
        if (responses == null || responses.isEmpty()) {
            return null;
        }
        ApiResponse success = null;
        int lowestStatus = Integer.MAX_VALUE;
        for (Map.Entry<String, ApiResponse> entry : responses.entrySet()) {
            int status = parseStatus(entry.getKey());
            if (status >= 200 && status < 300 && status < lowestStatus) {
                lowestStatus = status;
                success = entry.getValue();
            }
        }
        if (success == null) {
            return null;
        }
        MediaType mediaType = pickMediaType(success.getContent());
        return mediaType == null ? null : toJson(mediaType.getSchema());
    }

    private int parseStatus(String key) {
        try {
            return Integer.parseInt(key);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private MediaType pickMediaType(Content content) {
        if (content == null || content.isEmpty()) {
            return null;
        }
        MediaType preferred = content.get(PREFERRED_CONTENT_TYPE);
        return preferred != null ? preferred : content.values().iterator().next();
    }

    private JsonNode toJson(Schema<?> schema) {
        return schema == null ? null : MAPPER.convertValue(schema, JsonNode.class);
    }
}