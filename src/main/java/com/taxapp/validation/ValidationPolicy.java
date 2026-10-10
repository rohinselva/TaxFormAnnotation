package com.taxapp.validation;

/**
 * Defines how validation failures are handled during PDF generation.
 */
public enum ValidationPolicy {
    /**
     * Immediately aborts PDF generation with a ValidationException if any ERROR is detected.
     */
    STRICT,

    /**
     * Collects validation errors and logs them, but proceeds with PDF generation.
     */
    LENIENT,

    /**
     * Completely skips pre-flight data and specification validation checks.
     */
    SKIP
}
