package com.apiproject.diff;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeType;

final class SchemaDiffUtils {
    private SchemaDiffUtils() {}

    static boolean hasComplexComposition(JsonNode schema) {
        if (schema == null || schema.isMissingNode() || schema.isNull()) {
            return false;
        }
        if (schema.has("oneOf") || schema.has("anyOf")) {
            return true;
        }
        return false;
    }

    static boolean schemasStructurallyDifferent(JsonNode oldSchema, JsonNode newSchema) {
        if (oldSchema == null || oldSchema.isMissingNode() || oldSchema.isNull()) {
            if (newSchema == null || newSchema.isMissingNode() || newSchema.isNull()) {
                return false;
            }
            return true;
        }
        if (newSchema == null || newSchema.isMissingNode() || newSchema.isNull()) {
            return true;
        }
        return !oldSchema.equals(newSchema);
    }
}
