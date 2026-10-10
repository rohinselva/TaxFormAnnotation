package com.taxapp.validation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Aggregates validation errors, warnings, and informational diagnostics.
 */
public class ValidationResult {

    private final List<ValidationError> issues;

    public ValidationResult() {
        this.issues = new ArrayList<>();
    }

    public ValidationResult(List<ValidationError> issues) {
        this.issues = issues != null ? new ArrayList<>(issues) : new ArrayList<>();
    }

    public void addIssue(ValidationError error) {
        if (error != null) {
            this.issues.add(error);
        }
    }

    public void addError(String fieldPath, String errorCode, String message) {
        addIssue(new ValidationError(fieldPath, errorCode, message, ValidationSeverity.ERROR));
    }

    public void addWarning(String fieldPath, String errorCode, String message) {
        addIssue(new ValidationError(fieldPath, errorCode, message, ValidationSeverity.WARNING));
    }

    public void addInfo(String fieldPath, String errorCode, String message) {
        addIssue(new ValidationError(fieldPath, errorCode, message, ValidationSeverity.INFO));
    }

    public void merge(ValidationResult other) {
        if (other != null) {
            this.issues.addAll(other.issues);
        }
    }

    public boolean isValid() {
        return issues.stream().noneMatch(i -> i.severity() == ValidationSeverity.ERROR);
    }

    public boolean hasErrors() {
        return !isValid();
    }

    public boolean hasWarnings() {
        return issues.stream().anyMatch(i -> i.severity() == ValidationSeverity.WARNING);
    }

    public List<ValidationError> getIssues() {
        return Collections.unmodifiableList(issues);
    }

    public List<ValidationError> getErrors() {
        return issues.stream()
                .filter(i -> i.severity() == ValidationSeverity.ERROR)
                .collect(Collectors.toUnmodifiableList());
    }

    public List<ValidationError> getWarnings() {
        return issues.stream()
                .filter(i -> i.severity() == ValidationSeverity.WARNING)
                .collect(Collectors.toUnmodifiableList());
    }

    public int errorCount() {
        return (int) issues.stream().filter(i -> i.severity() == ValidationSeverity.ERROR).count();
    }

    public int warningCount() {
        return (int) issues.stream().filter(i -> i.severity() == ValidationSeverity.WARNING).count();
    }

    /**
     * Formats all validation issues into a readable multi-line summary report.
     */
    public String formatReport() {
        if (issues.isEmpty()) {
            return "Validation PASSED with 0 issues.";
        }
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Validation Report: %d error(s), %d warning(s)\n", errorCount(), warningCount()));
        for (ValidationError issue : issues) {
            sb.append(" - ").append(issue.toString()).append("\n");
        }
        return sb.toString();
    }
}
