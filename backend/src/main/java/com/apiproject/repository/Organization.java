package com.apiproject.repository;

/** Tenant root (scoped-repository.md rule 5, INV-ORG). Its own id IS its organization. */
public record Organization(String id, String name) {}