package com.apiproject.ingestion;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Dereferences an OpenAPI model per DECISION-003:
 * <ul>
 *   <li>every <code>$ref</code> to <code>#/components/schemas/&lt;name&gt;</code> is expanded to a
 *       fully-resolved schema (the diff engine must never see a <code>$ref</code>),</li>
 *   <li><code>allOf</code> is merged into ONE effective schema (property/required union; scalar
 *       conflicts first-wins; duplicate property names later-wins — v1 simplification, documented),</li>
 *   <li><code>oneOf</code>/<code>anyOf</code> are left structurally intact (NOT merged in v1),</li>
 *   <li>circular <code>$ref</code> chains (including legitimate recursive schemas) are rejected with
 *       {@link SpecResolutionException#CIRCULAR_REF}, per DECISION-003 and FR-003,</li>
 *   <li>refs to anything other than in-document schemas (external URLs, component parameters/
 *       requestBodies/responses) are rejected with {@link SpecResolutionException#UNSUPPORTED_REF}.</li>
 * </ul>
 *
 * <p>Parsing the YAML/JSON stays in the OpenAPI library (guardrail §6); this is a graph walk over the
 * parsed model, not a schema parser. Pure and deterministic: identical input model → identical output.
 *
 * <p><strong>Mutation semantics (explicit):</strong> the parsed model is mutated in place. Resolved
 * components are memoized and may be shared by multiple usage sites; callers (extractor, diff) must
 * treat the resolved model as read-only after {@link #resolve} returns.
 */
public final class SpecResolver {

    @SuppressWarnings("rawtypes")
    public OpenAPI resolve(OpenAPI openApi) {
        if (openApi == null) {
            return null;
        }
        Map<String, Schema> index = new LinkedHashMap<>();
        if (openApi.getComponents() != null && openApi.getComponents().getSchemas() != null) {
            index.putAll(openApi.getComponents().getSchemas());
        }
        ResolutionContext context = new ResolutionContext(index);

        if (openApi.getPaths() != null) {
            for (Map.Entry<String, PathItem> entry : openApi.getPaths().entrySet()) {
                resolvePathItem(context, entry.getValue());
            }
        }
        for (String name : index.keySet()) {
            if (!context.expanded.containsKey(name)) {
                context.resolveComponent(name);
            }
        }
        return openApi;
    }

    private void resolvePathItem(ResolutionContext context, PathItem pathItem) {
        if (pathItem == null) {
            return;
        }
        resolveParameters(context, pathItem.getParameters());
        for (PathItem.HttpMethod method : PathItem.HttpMethod.values()) {
            resolveOperation(context, operationOf(pathItem, method));
        }
    }

    private Operation operationOf(PathItem pathItem, PathItem.HttpMethod method) {
        return switch (method) {
            case GET -> pathItem.getGet();
            case PUT -> pathItem.getPut();
            case POST -> pathItem.getPost();
            case DELETE -> pathItem.getDelete();
            case PATCH -> pathItem.getPatch();
            case HEAD -> pathItem.getHead();
            case OPTIONS -> pathItem.getOptions();
            case TRACE -> pathItem.getTrace();
        };
    }

    private void resolveOperation(ResolutionContext context, Operation operation) {
        if (operation == null) {
            return;
        }
        resolveParameters(context, operation.getParameters());
        if (operation.getRequestBody() != null) {
            resolveContent(context, operation.getRequestBody().getContent());
        }
        resolveResponses(context, operation.getResponses());
    }

    @SuppressWarnings("rawtypes")
    private void resolveParameters(ResolutionContext context, List<Parameter> parameters) {
        if (parameters == null) {
            return;
        }
        for (Parameter parameter : parameters) {
            if (parameter.get$ref() != null) {
                throw context.failure(
                        SpecResolutionException.UNSUPPORTED_REF,
                        "component-level parameter refs are not supported in v1: " + parameter.get$ref());
            }
            if (parameter.getSchema() != null) {
                parameter.setSchema(resolveSchema(context, parameter.getSchema()));
            }
            resolveContent(context, parameter.getContent());
        }
    }

    @SuppressWarnings("rawtypes")
    private void resolveResponses(ResolutionContext context, ApiResponses responses) {
        if (responses == null) {
            return;
        }
        for (ApiResponse response : responses.values()) {
            if (response.get$ref() != null) {
                throw context.failure(
                        SpecResolutionException.UNSUPPORTED_REF,
                        "component-level response refs are not supported in v1: " + response.get$ref());
            }
            resolveContent(context, response.getContent());
        }
    }

    @SuppressWarnings("rawtypes")
    private void resolveContent(ResolutionContext context, Content content) {
        if (content == null) {
            return;
        }
        for (MediaType mediaType : content.values()) {
            mediaType.setSchema(resolveSchema(context, mediaType.getSchema()));
        }
    }

    /**
     * Returns the effective schema for {@code schema} (possibly a different instance): expanded for a
     * {@code $ref}, newly merged for {@code allOf}, otherwise the same node with children resolved.
     * Callers must re-bind the return value (e.g. {@code setSchema(...)}).
     */
    @SuppressWarnings("rawtypes")
    private Schema resolveSchema(ResolutionContext context, Schema schema) {
        if (schema == null) {
            return null;
        }
        String ref = schema.get$ref();
        if (ref != null) {
            String name = context.parseSchemaRef(ref);
            return context.resolveComponent(name);
        }
        if (schema.getAllOf() != null && !schema.getAllOf().isEmpty()) {
            resolveSelfChildren(context, schema);
            Schema merged = new Schema();
            copyDirectFields(schema, merged);
            List allOf = schema.getAllOf();
            for (int i = 0; i < allOf.size(); i++) {
                merged = mergeTogether(merged, resolveSchema(context, (Schema) allOf.get(i)));
            }
            return merged;
        }
        resolveSelfChildren(context, schema);
        return schema;
    }

    /** Mutates {@code schema}'s own children in place (properties, items, compositions, . . .). */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    private void resolveSelfChildren(ResolutionContext context, Schema schema) {
        Map<String, Schema> props = schema.getProperties();
        if (props != null) {
            for (String key : new ArrayList<>(props.keySet())) {
                props.put(key, resolveSchema(context, props.get(key)));
            }
        }
        if (schema.getItems() != null) {
            schema.setItems(resolveSchema(context, schema.getItems()));
        }
        if (schema.getAdditionalProperties() instanceof Schema additional) {
            schema.setAdditionalProperties(resolveSchema(context, additional));
        }
        replaceCompositionList(context, schema.getAllOf());
        replaceCompositionList(context, schema.getOneOf());
        replaceCompositionList(context, schema.getAnyOf());
        if (schema.getNot() != null) {
            schema.setNot(resolveSchema(context, schema.getNot()));
        }
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private void replaceCompositionList(ResolutionContext context, List schemas) {
        if (schemas == null) {
            return;
        }
        for (int i = 0; i < schemas.size(); i++) {
            schemas.set(i, resolveSchema(context, (Schema) schemas.get(i)));
        }
    }

    @SuppressWarnings("rawtypes")
    private void copyDirectFields(Schema from, Schema to) {
        to.setType(from.getType());
        to.setFormat(from.getFormat());
        to.setTitle(from.getTitle());
        to.setDescription(from.getDescription());
        if (from.getProperties() != null) {
            to.setProperties(new LinkedHashMap<>(from.getProperties()));
        }
        if (from.getRequired() != null) {
            to.setRequired(new ArrayList<>(from.getRequired()));
        }
        to.setItems(from.getItems());
        to.setAdditionalProperties(from.getAdditionalProperties());
        to.setEnum(from.getEnum());
        to.setNullable(from.getNullable());
        to.setExample(from.getExample());
        to.setMinLength(from.getMinLength());
        to.setMaxLength(from.getMaxLength());
        to.setMinimum(from.getMinimum());
        to.setMaximum(from.getMaximum());
        to.setExclusiveMinimum(from.getExclusiveMinimum());
        to.setExclusiveMaximum(from.getExclusiveMaximum());
        to.setPattern(from.getPattern());
        to.setUniqueItems(from.getUniqueItems());
        to.setMinItems(from.getMinItems());
        to.setMaxItems(from.getMaxItems());
        to.setOneOf(from.getOneOf());
        to.setAnyOf(from.getAnyOf());
        to.setNot(from.getNot());
        to.setReadOnly(from.getReadOnly());
        to.setWriteOnly(from.getWriteOnly());
    }

    /**
     * v1 merge rule (documented): property/required are unioned (duplicate property = later-wins);
     * all scalar constraints are first-wins. No <code>$ref</code> survives on the merged node.
     */
    @SuppressWarnings("rawtypes")
    private Schema mergeTogether(Schema first, Schema second) {
        Schema out = new Schema();
        out.setType(first.getType() != null ? first.getType() : second.getType());
        out.setFormat(first.getFormat() != null ? first.getFormat() : second.getFormat());
        out.setTitle(first.getTitle() != null ? first.getTitle() : second.getTitle());
        out.setDescription(first.getDescription() != null ? first.getDescription() : second.getDescription());
        out.setNullable(first.getNullable() != null ? first.getNullable() : second.getNullable());
        out.setExample(first.getExample() != null ? first.getExample() : second.getExample());
        out.setMinLength(first.getMinLength() != null ? first.getMinLength() : second.getMinLength());
        out.setMaxLength(first.getMaxLength() != null ? first.getMaxLength() : second.getMaxLength());
        out.setMinimum(first.getMinimum() != null ? first.getMinimum() : second.getMinimum());
        out.setMaximum(first.getMaximum() != null ? first.getMaximum() : second.getMaximum());
        out.setExclusiveMinimum(
                first.getExclusiveMinimum() != null ? first.getExclusiveMinimum() : second.getExclusiveMinimum());
        out.setExclusiveMaximum(
                first.getExclusiveMaximum() != null ? first.getExclusiveMaximum() : second.getExclusiveMaximum());
        out.setPattern(first.getPattern() != null ? first.getPattern() : second.getPattern());
        out.setUniqueItems(first.getUniqueItems() != null ? first.getUniqueItems() : second.getUniqueItems());
        out.setMinItems(first.getMinItems() != null ? first.getMinItems() : second.getMinItems());
        out.setMaxItems(first.getMaxItems() != null ? first.getMaxItems() : second.getMaxItems());
        out.setEnum(first.getEnum() != null ? first.getEnum() : second.getEnum());
        out.setItems(first.getItems() != null ? first.getItems() : second.getItems());
        out.setAdditionalProperties(
                first.getAdditionalProperties() != null
                        ? first.getAdditionalProperties()
                        : second.getAdditionalProperties());
        out.setOneOf(first.getOneOf() != null ? first.getOneOf() : second.getOneOf());
        out.setAnyOf(first.getAnyOf() != null ? first.getAnyOf() : second.getAnyOf());
        out.setNot(first.getNot() != null ? first.getNot() : second.getNot());
        out.setReadOnly(first.getReadOnly() != null ? first.getReadOnly() : second.getReadOnly());
        out.setWriteOnly(first.getWriteOnly() != null ? first.getWriteOnly() : second.getWriteOnly());

        Map<String, Schema> combined = new LinkedHashMap<>();
        if (first.getProperties() != null) {
            combined.putAll(first.getProperties());
        }
        if (second.getProperties() != null) {
            combined.putAll(second.getProperties());
        }
        out.setProperties(combined.isEmpty() ? null : combined);

        Set<String> required = new LinkedHashSet<>();
        if (first.getRequired() != null) {
            required.addAll(first.getRequired());
        }
        if (second.getRequired() != null) {
            required.addAll(second.getRequired());
        }
        out.setRequired(required.isEmpty() ? null : new ArrayList<>(required));
        return out;
    }

    @SuppressWarnings("rawtypes")
    private final class ResolutionContext {
        private final Map<String, Schema> components;
        private final Map<String, Schema> expanded = new LinkedHashMap<>();
        private final Deque<String> stack = new ArrayDeque<>();

        private ResolutionContext(Map<String, Schema> components) {
            this.components = components;
        }

        private String parseSchemaRef(String ref) {
            if (ref == null || !ref.startsWith("#/")) {
                throw failure(SpecResolutionException.UNSUPPORTED_REF, "unsupported $ref: " + ref);
            }
            String[] parts = ref.split("/");
            if (parts.length != 4 || !"components".equals(parts[1]) || !"schemas".equals(parts[2])) {
                throw failure(SpecResolutionException.UNSUPPORTED_REF, "unsupported $ref: " + ref);
            }
            return parts[3];
        }

        private Schema resolveComponent(String name) {
            Schema memoized = expanded.get(name);
            if (memoized != null) {
                return memoized;
            }
            Schema component = components.get(name);
            if (component == null) {
                throw failure(SpecResolutionException.UNKNOWN_REF, "unknown schema component: " + name);
            }
            if (stack.contains(name)) {
                throw failure(
                        SpecResolutionException.CIRCULAR_REF,
                        "circular $ref chain detected at #/components/schemas/" + name
                                + " (rejected per DECISION-003)");
            }
            stack.push(name);
            try {
                Schema resolved = resolveSchema(this, component);
                expanded.put(name, resolved);
                return resolved;
            } finally {
                stack.pop();
            }
        }

        private SpecResolutionException failure(String code, String message) {
            return new SpecResolutionException(code, message);
        }
    }
}