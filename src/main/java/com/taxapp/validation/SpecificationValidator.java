package com.taxapp.validation;

import com.jayway.jsonpath.JsonPath;
import com.taxapp.model.FieldAnnotation;
import com.taxapp.model.FormSpecification;

import java.util.HashSet;
import java.util.Set;

/**
 * Validates FormSpecification definitions for structural and coordinate integrity.
 */
public class SpecificationValidator {

    public ValidationResult validate(FormSpecification spec) {
        ValidationResult result = new ValidationResult();

        if (spec == null) {
            result.addError("spec", "ERR_SPEC_NULL", "Form specification cannot be null");
            return result;
        }

        if (spec.formId() == null || spec.formId().trim().isEmpty()) {
            result.addError("spec.formId", "ERR_FORM_ID_MISSING", "Specification formId is required");
        }

        if (spec.taxYear() < 2000 || spec.taxYear() > 2100) {
            result.addWarning("spec.taxYear", "WARN_TAX_YEAR_RANGE", "Unusual tax year: " + spec.taxYear());
        }

        if (spec.pages() <= 0) {
            result.addError("spec.pages", "ERR_PAGES_INVALID", "Specification page count must be greater than zero");
        }

        double pageWidth = 612.0;
        double pageHeight = 792.0;

        if (spec.pageDimensions() != null) {
            if (spec.pageDimensions().width() <= 0 || spec.pageDimensions().height() <= 0) {
                result.addError("spec.pageDimensions", "ERR_DIMENSIONS_INVALID", "Page width and height must be positive");
            } else {
                pageWidth = spec.pageDimensions().width();
                pageHeight = spec.pageDimensions().height();
            }
        } else {
            result.addWarning("spec.pageDimensions", "WARN_DIMENSIONS_MISSING", "Page dimensions not specified; defaulting to 612x792 pt");
        }

        if (spec.fields() == null || spec.fields().isEmpty()) {
            result.addError("spec.fields", "ERR_FIELDS_EMPTY", "Specification must contain at least one field definition");
            return result;
        }

        Set<String> seenFieldIds = new HashSet<>();

        for (int i = 0; i < spec.fields().size(); i++) {
            FieldAnnotation field = spec.fields().get(i);
            String path = "spec.fields[" + i + "]";

            if (field == null) {
                result.addError(path, "ERR_FIELD_NULL", "Field annotation entry is null");
                continue;
            }

            // 1. Unique Field ID check
            if (field.fieldId() == null || field.fieldId().trim().isEmpty()) {
                result.addError(path, "ERR_FIELD_ID_MISSING", "Field annotation is missing fieldId");
            } else if (!seenFieldIds.add(field.fieldId())) {
                result.addError(path, "ERR_DUPLICATE_FIELD_ID", "Duplicate fieldId detected: " + field.fieldId());
            }

            // 2. Page bounds check
            if (field.page() < 1 || (spec.pages() > 0 && field.page() > spec.pages())) {
                result.addError(path, "ERR_PAGE_OUT_OF_BOUNDS", 
                        String.format("Field '%s' targets page %d, but form has %d pages", field.fieldId(), field.page(), spec.pages()));
            }

            // 3. Coordinate bounds check
            if (field.position() == null) {
                result.addError(path, "ERR_POSITION_NULL", "Field '" + field.fieldId() + "' is missing position coordinates");
            } else {
                double x = field.position().x();
                double y = field.position().y();
                double w = field.position().width();
                double h = field.position().height();

                if (w <= 0 || h <= 0) {
                    result.addError(path, "ERR_DIMENSIONS_NON_POSITIVE",
                            String.format("Field '%s' width (%f) and height (%f) must be positive", field.fieldId(), w, h));
                }

                if (x < 0 || y < 0) {
                    result.addError(path, "ERR_COORDINATES_NEGATIVE",
                            String.format("Field '%s' x (%f) and y (%f) coordinates cannot be negative", field.fieldId(), x, y));
                }

                if (x + w > pageWidth + 0.5) { // allow minor floating precision
                    result.addError(path, "ERR_FIELD_OVERFLOW_X",
                            String.format("Field '%s' x + width (%f) overflows page width (%f)", field.fieldId(), x + w, pageWidth));
                }

                if (y + h > pageHeight + 0.5) {
                    result.addError(path, "ERR_FIELD_OVERFLOW_Y",
                            String.format("Field '%s' y + height (%f) overflows page height (%f)", field.fieldId(), y + h, pageHeight));
                }
            }

            // 4. DataPath JSONPath syntax check
            if (field.dataPath() == null || field.dataPath().trim().isEmpty()) {
                result.addError(path, "ERR_DATAPATH_MISSING", "Field '" + field.fieldId() + "' is missing dataPath");
            } else if (!field.dataPath().startsWith("$")) {
                result.addError(path, "ERR_DATAPATH_INVALID_PREFIX", "Field '" + field.fieldId() + "' dataPath must begin with '$': " + field.dataPath());
            } else {
                try {
                    JsonPath.compile(field.dataPath());
                } catch (Exception e) {
                    result.addError(path, "ERR_JSONPATH_SYNTAX", "Invalid JSONPath expression in field '" + field.fieldId() + "': " + e.getMessage());
                }
            }
        }

        return result;
    }
}
