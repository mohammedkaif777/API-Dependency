package com.apiproject.diff;

import com.apiproject.ingestion.ExtractedEndpoint;
import com.apiproject.ingestion.ExtractedParameter;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class ApiDiffEngine {

    private static final String COMPOSITION_REASON = "complex schema composition changed — manual review required";

    private ApiDiffEngine() {
    }

    public static List<ApiChange> compare(ApiVersion oldVersion, ApiVersion newVersion) {
        Objects.requireNonNull(oldVersion, "oldVersion");
        Objects.requireNonNull(newVersion, "newVersion");

        String organizationId = oldVersion.organizationId();
        String serviceId = oldVersion.serviceId();
        if (!organizationId.equals(newVersion.organizationId()) || !serviceId.equals(newVersion.serviceId())) {
            throw new IllegalArgumentException(
                    "old and new versions must belong to same organization and service");
        }

        String apiId = oldVersion.apiId().isBlank() ? newVersion.apiId() : oldVersion.apiId();
        if (apiId.isBlank() && !newVersion.apiId().isBlank()) {
            apiId = newVersion.apiId();
        }

        Map<Key, ExtractedEndpoint> oldMap = indexByKey(oldVersion.endpoints());
        Map<Key, ExtractedEndpoint> newMap = indexByKey(newVersion.endpoints());

        List<ApiChange> changes = new ArrayList<>();

        // Detect removed endpoints
        for (Map.Entry<Key, ExtractedEndpoint> entry : oldMap.entrySet()) {
            if (!newMap.containsKey(entry.getKey())) {
                Key key = entry.getKey();
                changes.add(new ApiChange(
                        organizationId,
                        serviceId,
                        apiId,
                        key.path,
                        key.method,
                        ChangeType.ENDPOINT_REMOVED,
                        ChangeClassification.BREAKING,
                        "endpoint removed: " + key.method + " " + key.path,
                        "path=" + key.path + ", method=" + key.method));
            }
        }

        // Detect added endpoints and compare common endpoints
        for (Map.Entry<Key, ExtractedEndpoint> entry : newMap.entrySet()) {
            Key key = entry.getKey();
            if (!oldMap.containsKey(key)) {
                changes.add(new ApiChange(
                        organizationId,
                        serviceId,
                        apiId,
                        key.path,
                        key.method,
                        ChangeType.ENDPOINT_ADDED,
                        ChangeClassification.NON_BREAKING,
                        "endpoint added: " + key.method + " " + key.path,
                        "path=" + key.path + ", method=" + key.method));
            } else {
                compareEndpoints(organizationId, serviceId, apiId, key, oldMap.get(key), entry.getValue(), changes);
            }
        }

        changes.sort(Comparator
                .comparing(ApiChange::changeType)
                .thenComparing(ApiChange::affectedPath)
                .thenComparing(ApiChange::method)
                .thenComparing(ApiChange::reason));

        return changes;
    }

    private static void compareEndpoints(String orgId, String svcId, String apiId, Key key,
            ExtractedEndpoint oldEp, ExtractedEndpoint newEp, List<ApiChange> changes) {
        compareParameters(orgId, svcId, apiId, key, oldEp, newEp, changes);
        compareRequestBody(orgId, svcId, apiId, key, oldEp, newEp, changes);
        compareResponse(orgId, svcId, apiId, key, oldEp, newEp, changes);
    }

    private static void compareParameters(String orgId, String svcId, String apiId, Key key,
            ExtractedEndpoint oldEp, ExtractedEndpoint newEp, List<ApiChange> changes) {
        Map<ParamKey, ExtractedParameter> oldParams = indexParams(oldEp.parameters());
        Map<ParamKey, ExtractedParameter> newParams = indexParams(newEp.parameters());

        // Removed params
        for (Map.Entry<ParamKey, ExtractedParameter> e : oldParams.entrySet()) {
            if (!newParams.containsKey(e.getKey())) {
                ParamKey pk = e.getKey();
                changes.add(new ApiChange(
                        orgId,
                        svcId,
                        apiId,
                        key.path,
                        key.method,
                        ChangeType.PARAM_REMOVED,
                        ChangeClassification.BREAKING,
                        "parameter removed: " + pk.name + " (" + pk.in + ")",
                        "path=" + key.path + ", method=" + key.method + ", param=" + pk.name + ", in=" + pk.in));
            }
        }

        // Added and changed params
        for (Map.Entry<ParamKey, ExtractedParameter> e : newParams.entrySet()) {
            ParamKey pk = e.getKey();
            if (!oldParams.containsKey(pk)) {
                ExtractedParameter np = e.getValue();
                boolean isRequired = np != null && np.required();
                changes.add(new ApiChange(
                        orgId,
                        svcId,
                        apiId,
                        key.path,
                        key.method,
                        ChangeType.PARAM_ADDED,
                        isRequired ? ChangeClassification.BREAKING : ChangeClassification.NON_BREAKING,
                        "parameter added: " + pk.name + " (" + pk.in + ")" + (isRequired ? " (required)" : ""),
                        "path=" + key.path + ", method=" + key.method + ", param=" + pk.name + ", in=" + pk.in
                                + ", required=" + isRequired));
            } else {
                compareParameter(orgId, svcId, apiId, key, pk, oldParams.get(pk), e.getValue(), changes);
            }
        }
    }

    private static void compareParameter(String orgId, String svcId, String apiId, Key key, ParamKey pk,
            ExtractedParameter oldP, ExtractedParameter newP, List<ApiChange> changes) {
        JsonNode oldSchema = oldP != null ? oldP.schema() : null;
        JsonNode newSchema = newP != null ? newP.schema() : null;

        // Composition check takes precedence
        if (SchemaDiffUtils.hasComplexComposition(oldSchema) || SchemaDiffUtils.hasComplexComposition(newSchema)) {
            changes.add(new ApiChange(
                    orgId,
                    svcId,
                    apiId,
                    key.path,
                    key.method,
                    ChangeType.SCHEMA_COMPOSITION_CHANGED,
                    ChangeClassification.POTENTIALLY_BREAKING,
                    COMPOSITION_REASON,
                    "path=" + key.path + ", method=" + key.method + ", param=" + pk.name + ", in=" + pk.in));
            return;
        }

        boolean oldReq = oldP != null && oldP.required();
        boolean newReq = newP != null && newP.required();
        if (oldReq != newReq) {
            if (oldReq && !newReq) {
                changes.add(new ApiChange(
                        orgId,
                        svcId,
                        apiId,
                        key.path,
                        key.method,
                        ChangeType.PARAM_REQUIRED_CHANGED,
                        ChangeClassification.NON_BREAKING,
                        "parameter required changed: " + pk.name + " (" + pk.in + ") true→false",
                        "path=" + key.path + ", method=" + key.method + ", param=" + pk.name + ", in=" + pk.in));
            } else if (!oldReq && newReq) {
                changes.add(new ApiChange(
                        orgId,
                        svcId,
                        apiId,
                        key.path,
                        key.method,
                        ChangeType.PARAM_REQUIRED_CHANGED,
                        ChangeClassification.BREAKING,
                        "parameter required changed: " + pk.name + " (" + pk.in + ") false→true",
                        "path=" + key.path + ", method=" + key.method + ", param=" + pk.name + ", in=" + pk.in));
            }
            // If they become equal, no change recorded for required flag
        }

        if (SchemaDiffUtils.schemasStructurallyDifferent(oldSchema, newSchema)) {
            changes.add(new ApiChange(
                    orgId,
                    svcId,
                    apiId,
                    key.path,
                    key.method,
                    ChangeType.PARAM_TYPE_CHANGED,
                    ChangeClassification.POTENTIALLY_BREAKING,
                    "parameter schema/type changed (manual review required)",
                    "path=" + key.path + ", method=" + key.method + ", param=" + pk.name + ", in=" + pk.in));
        }
    }

    private static void compareRequestBody(String orgId, String svcId, String apiId, Key key,
            ExtractedEndpoint oldEp, ExtractedEndpoint newEp, List<ApiChange> changes) {
        JsonNode oldReq = oldEp.requestBodySchema();
        JsonNode newReq = newEp.requestBodySchema();

        if (SchemaDiffUtils.hasComplexComposition(oldReq) || SchemaDiffUtils.hasComplexComposition(newReq)) {
            changes.add(new ApiChange(
                    orgId,
                    svcId,
                    apiId,
                    key.path,
                    key.method,
                    ChangeType.SCHEMA_COMPOSITION_CHANGED,
                    ChangeClassification.POTENTIALLY_BREAKING,
                    COMPOSITION_REASON,
                    "path=" + key.path + ", method=" + key.method + ", element=requestBody"));
            return;
        }

        if (SchemaDiffUtils.schemasStructurallyDifferent(oldReq, newReq)) {
            changes.add(new ApiChange(
                    orgId,
                    svcId,
                    apiId,
                    key.path,
                    key.method,
                    ChangeType.REQUEST_BODY_CHANGED,
                    ChangeClassification.POTENTIALLY_BREAKING,
                    "requestBody schema changed — manual review required",
                    "path=" + key.path + ", method=" + key.method + ", element=requestBody"));
        }
    }

    private static void compareResponse(String orgId, String svcId, String apiId, Key key,
            ExtractedEndpoint oldEp, ExtractedEndpoint newEp, List<ApiChange> changes) {
        JsonNode oldResp = oldEp.responseSchema();
        JsonNode newResp = newEp.responseSchema();

        if (SchemaDiffUtils.hasComplexComposition(oldResp) || SchemaDiffUtils.hasComplexComposition(newResp)) {
            changes.add(new ApiChange(
                    orgId,
                    svcId,
                    apiId,
                    key.path,
                    key.method,
                    ChangeType.SCHEMA_COMPOSITION_CHANGED,
                    ChangeClassification.POTENTIALLY_BREAKING,
                    COMPOSITION_REASON,
                    "path=" + key.path + ", method=" + key.method + ", element=response"));
            return;
        }

        if (SchemaDiffUtils.schemasStructurallyDifferent(oldResp, newResp)) {
            changes.add(new ApiChange(
                    orgId,
                    svcId,
                    apiId,
                    key.path,
                    key.method,
                    ChangeType.RESPONSE_CHANGED,
                    ChangeClassification.POTENTIALLY_BREAKING,
                    "response schema changed — manual review required",
                    "path=" + key.path + ", method=" + key.method + ", element=response"));
        }
    }

    private static Map<Key, ExtractedEndpoint> indexByKey(List<ExtractedEndpoint> endpoints) {
        return endpoints.stream()
                .collect(Collectors.toMap(
                        e -> new Key(e.method(), e.path()),
                        Function.identity(),
                        (a, b) -> a,
                        TreeMap::new));
    }

    private static Map<ParamKey, ExtractedParameter> indexParams(List<ExtractedParameter> params) {
        if (params == null) {
            return new HashMap<>();
        }
        Map<ParamKey, ExtractedParameter> map = new TreeMap<>();
        for (ExtractedParameter p : params) {
            if (p == null) {
                continue;
            }
            String name = p.name() == null ? "" : p.name();
            String in = p.in() == null ? "" : p.in();
            map.put(new ParamKey(name, in), p);
        }
        return map;
    }

    private record Key(String method, String path) implements Comparable<Key> {
        @Override
        public int compareTo(Key other) {
            int cmp = method.compareToIgnoreCase(other.method);
            if (cmp != 0) {
                return cmp;
            }
            return path.compareTo(other.path);
        }
    }

    private record ParamKey(String name, String in) implements Comparable<ParamKey> {
        @Override
        public int compareTo(ParamKey other) {
            int cmp = name.compareToIgnoreCase(other.name);
            if (cmp != 0) {
                return cmp;
            }
            return in.compareToIgnoreCase(other.in);
        }
    }
}
