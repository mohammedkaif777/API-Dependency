package com.apiproject.graph;

/** A single "consumer calls provider endpoint" fact, resolved to its owning service for queries. */
public record ProviderCall(String providerEndpointId, String providerServiceId) {}