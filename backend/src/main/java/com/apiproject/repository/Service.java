package com.apiproject.repository;

/** Persistence-layer model of a Service (consumer or provider). Tenant-scoped per DECISION-002. */
public record Service(String id, String organizationId, String name) {}