package com.apiproject.diff;

import com.apiproject.ingestion.ExtractedEndpoint;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public record ApiVersion(
        String organizationId,
        String serviceId,
        String apiId,
        List<ExtractedEndpoint> endpoints) {
    public ApiVersion {
        Objects.requireNonNull(organizationId, "organizationId");
        Objects.requireNonNull(serviceId, "serviceId");
        endpoints = endpoints == null ? Collections.emptyList() : List.copyOf(endpoints);
        apiId = apiId == null ? "" : apiId;
    }
}
