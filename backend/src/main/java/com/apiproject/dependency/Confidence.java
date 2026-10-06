package com.apiproject.dependency;

/**
 * Confidence in a dependency's discovery. All values are retained for future phases so the persisted
 * schema never needs a breaking migration (requirements v2, DECISION-001); only {@link #DECLARED} is
 * accepted by MVP validation.
 */
public enum Confidence {
    DECLARED,
    INFERRED,
    OBSERVED
}