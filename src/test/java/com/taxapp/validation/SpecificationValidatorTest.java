package com.taxapp.validation;

import com.taxapp.model.FieldAnnotation;
import com.taxapp.model.FormSpecification;
import com.taxapp.model.FormatType;
import com.taxapp.model.PageDimensions;
import com.taxapp.model.Position;
import com.taxapp.model.TextAlignment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SpecificationValidatorTest {

    private SpecificationValidator validator;

    @BeforeEach
    void setUp() {
        validator = new SpecificationValidator();
    }

    @Test
    @DisplayName("Should pass valid FormSpecification")
    void testValidSpecification() {
        FormSpecification spec = new FormSpecification(
                "IRS-1040",
                2025,
                2,
                "POINTS",
                new PageDimensions(612.0, 792.0),
                List.of(
                        new FieldAnnotation("line_1a", "1a", 1, new Position(100, 100, 50, 12),
                                FormatType.CURRENCY_ROUNDED, TextAlignment.RIGHT, "$.income.w2Wages")
                )
        );

        ValidationResult result = validator.validate(spec);
        assertTrue(result.isValid(), "Valid spec should pass: " + result.formatReport());
        assertEquals(0, result.errorCount());
    }

    @Test
    @DisplayName("Should detect duplicate field IDs in specification")
    void testDuplicateFieldId() {
        FormSpecification spec = new FormSpecification(
                "IRS-1040",
                2025,
                2,
                "POINTS",
                new PageDimensions(612.0, 792.0),
                List.of(
                        new FieldAnnotation("dup_id", "1a", 1, new Position(100, 100, 50, 12),
                                FormatType.CURRENCY_ROUNDED, TextAlignment.RIGHT, "$.income.w2Wages"),
                        new FieldAnnotation("dup_id", "1b", 1, new Position(100, 120, 50, 12),
                                FormatType.CURRENCY_ROUNDED, TextAlignment.RIGHT, "$.income.other")
                )
        );

        ValidationResult result = validator.validate(spec);
        assertTrue(result.hasErrors());
        assertTrue(result.getErrors().stream().anyMatch(e -> "ERR_DUPLICATE_FIELD_ID".equals(e.errorCode())));
    }

    @Test
    @DisplayName("Should detect coordinates overflowing page boundaries")
    void testOverflowCoordinates() {
        FormSpecification spec = new FormSpecification(
                "IRS-1040",
                2025,
                2,
                "POINTS",
                new PageDimensions(612.0, 792.0),
                List.of(
                        new FieldAnnotation("overflow_x", "1", 1, new Position(600, 100, 50, 12), // 600 + 50 = 650 > 612
                                FormatType.UPPERCASE_TEXT, TextAlignment.LEFT, "$.user.name")
                )
        );

        ValidationResult result = validator.validate(spec);
        assertTrue(result.hasErrors());
        assertTrue(result.getErrors().stream().anyMatch(e -> "ERR_FIELD_OVERFLOW_X".equals(e.errorCode())));
    }

    @Test
    @DisplayName("Should detect invalid JSONPath expression in specification")
    void testInvalidJsonPath() {
        FormSpecification spec = new FormSpecification(
                "IRS-1040",
                2025,
                2,
                "POINTS",
                new PageDimensions(612.0, 792.0),
                List.of(
                        new FieldAnnotation("invalid_path", "1", 1, new Position(100, 100, 50, 12),
                                FormatType.UPPERCASE_TEXT, TextAlignment.LEFT, "user.name.missing.dollar")
                )
        );

        ValidationResult result = validator.validate(spec);
        assertTrue(result.hasErrors());
        assertTrue(result.getErrors().stream().anyMatch(e -> "ERR_DATAPATH_INVALID_PREFIX".equals(e.errorCode())));
    }
}
