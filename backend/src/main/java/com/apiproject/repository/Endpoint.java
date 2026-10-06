package com.apiproject.repository;

/**
 * Persistence-layer model of an Endpoint (provider target for a Dependency). Carries the owning
 * service id directly so the dependency validator can resolve INV-DEP-04 without joining Api rows;
 * the Api chain and method/path metadata land when Api/Endpoint snapshots are persisted (P5/P8).
 */
public record Endpoint(String id, String organizationId, String serviceId) {}