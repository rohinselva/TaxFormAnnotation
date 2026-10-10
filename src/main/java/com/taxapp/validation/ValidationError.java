package com.taxapp.validation;

/**
 * Encapsulates an individual validation issue detected in taxpayer data or form specifications.
 *
 * @param fieldPath Path or field ID where the issue occurred (e.g. "$.taxpayer.ssn" or "line_1a")
 * @param errorCode Unique alphanumeric code identifying the validation rule (e.g. "ERR_SSN_INVALID")
 * @param message Human-readable explanation of the validation failure
 * @param severity Severity level (ERROR, WARNING, INFO)
 */
public record ValidationError(
    String fieldPath,
    String errorCode,
    String message,
    ValidationSeverity severity
) {
    public ValidationError {
        if (fieldPath == null) {
            fieldPath = "unknown";
        }
        if (errorCode == null) {
            errorCode = "ERR_GENERIC";
        }
        if (message == null) {
            message = "Validation check failed";
        }
        if (severity == null) {
            severity = ValidationSeverity.ERROR;
        }
    }

    @Override
    public String toString() {
        return String.format("[%s] %s (%s): %s", severity, errorCode, fieldPath, message);
    }
}
