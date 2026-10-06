package com.apiproject.dependency;

/**
 * How a dependency was discovered. All values are retained for future phases so the persisted schema
 * never needs a breaking migration (requirements v2, DECISION-001); only {@link #MANUAL} is accepted
 * by MVP validation (declaring anything else fails with {@code UNSUPPORTED_DETECTION_SOURCE}).
 */
public enum DetectionSource {
    MANUAL,
    STATIC_CODE_ANALYSIS,
    RUNTIME
}