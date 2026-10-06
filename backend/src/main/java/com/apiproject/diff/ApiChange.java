package com.apiproject.diff;

import java.util.Objects;

public record ApiChange(
        String organizationId,
        String serviceId,
        String apiId,
        String affectedPath,
        String method,
        ChangeType changeType,
        ChangeClassification classification,
        String reason,
        String evidence) {
    public ApiChange {
        Objects.requireNonNull(organizationId, "organizationId");
        Objects.requireNonNull(serviceId, "serviceId");
        Objects.requireNonNull(changeType, "changeType");
        Objects.requireNonNull(classification, "classification");
        Objects.requireNonNull(reason, "reason");
        affectedPath = affectedPath == null ? "" : affectedPath;
        method = method == null ? "" : method;
        apiId = apiId == null ? "" : apiId;
        evidence = evidence == null ? "" : evidence;
    }
}
