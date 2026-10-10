package com.taxapp.validation;

/**
 * Severity levels for tax form and specification validation issues.
 */
public enum ValidationSeverity {
    /**
     * Critical issue that violates IRS filing specifications or schema invariants.
     */
    ERROR,

    /**
     * Potential anomaly or data irregularity that does not strictly violate the schema.
     */
    WARNING,

    /**
     * Informational notice regarding formatting or field defaults.
     */
    INFO
}
