package com.taxapp.validation;

import java.util.List;

/**
 * Thrown when validation fails under a STRICT validation policy.
 */
public class ValidationException extends RuntimeException {

    private final ValidationResult validationResult;

    public ValidationException(String message, ValidationResult validationResult) {
        super(message + "\n" + (validationResult != null ? validationResult.formatReport() : ""));
        this.validationResult = validationResult;
    }

    public ValidationResult getValidationResult() {
        return validationResult;
    }

    public List<ValidationError> getErrors() {
        return validationResult != null ? validationResult.getErrors() : List.of();
    }
}
