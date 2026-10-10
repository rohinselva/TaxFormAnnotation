package com.taxapp.validation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class TaxDataValidatorTest {

    private TaxDataValidator validator;

    @BeforeEach
    void setUp() {
        validator = new TaxDataValidator();
    }

    @Test
    @DisplayName("Should pass valid taxpayer payload")
    void testValidPayload() {
        String validJson = """
                {
                    "taxpayer": {
                        "firstNameAndInitial": "Jane",
                        "lastName": "Doe",
                        "ssn": "123456789",
                        "signatureDate": "2026-04-15"
                    },
                    "filingStatus": {
                        "single": true
                    },
                    "refund": {
                        "routingNumber": "021000021",
                        "isCheckingAccount": true
                    },
                    "income": {
                        "w2Wages": 95000.00
                    }
                }
                """;

        ValidationResult result = validator.validate(validJson);
        assertTrue(result.isValid(), "Payload should be valid: " + result.formatReport());
        assertEquals(0, result.errorCount());
    }

    @Test
    @DisplayName("Should detect missing taxpayer SSN")
    void testMissingSsn() {
        String json = """
                {
                    "taxpayer": {
                        "firstNameAndInitial": "Jane"
                    }
                }
                """;

        ValidationResult result = validator.validate(json);
        assertTrue(result.hasErrors());
        assertTrue(result.getErrors().stream().anyMatch(e -> "ERR_SSN_MISSING".equals(e.errorCode())));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "000123456", // Area cannot be 000
            "666123456", // Area cannot be 666
            "900123456", // Area cannot be 900+
            "123004567", // Group cannot be 00
            "123450000", // Serial cannot be 0000
            "12345678",  // 8 digits (too short)
            "1234567890" // 10 digits (too long)
    })
    @DisplayName("Should reject invalid SSN patterns according to IRS rules")
    void testInvalidSsnPatterns(String invalidSsn) {
        ValidationResult result = new ValidationResult();
        boolean valid = validator.validateSsnFormat("test.ssn", invalidSsn, result);
        assertFalse(valid);
        assertTrue(result.hasErrors());
    }

    @Test
    @DisplayName("Should accept valid Federal Reserve ABA routing transit numbers")
    void testValidRoutingNumbers() {
        ValidationResult result = new ValidationResult();
        // Chase Manhattan / JPMorgan Chase: 021000021 -> (3*(0+0+0) + 7*(2+0+2) + 1*(1+0+1)) = 0 + 28 + 2 = 30 % 10 == 0
        assertTrue(validator.validateRoutingNumber("test.routing", "021000021", result));
        assertFalse(result.hasErrors());

        // Bank of America: 121000358 -> (3*(1+0+3) + 7*(2+0+5) + 1*(1+0+8)) = 3*4 + 7*7 + 9 = 12 + 49 + 9 = 70 % 10 == 0
        assertTrue(validator.validateRoutingNumber("test.routing", "121000358", result));
        assertFalse(result.hasErrors());
    }

    @Test
    @DisplayName("Should reject invalid ABA routing numbers failing checksum")
    void testInvalidRoutingChecksum() {
        ValidationResult result = new ValidationResult();
        // Tampered digit: 021000022
        assertFalse(validator.validateRoutingNumber("test.routing", "021000022", result));
        assertTrue(result.hasErrors());
        assertTrue(result.getErrors().stream().anyMatch(e -> "ERR_ROUTING_CHECKSUM".equals(e.errorCode())));
    }

    @Test
    @DisplayName("Should detect conflict when both checking and savings accounts are selected")
    void testAccountTypeConflict() {
        String json = """
                {
                    "taxpayer": { "ssn": "123456789" },
                    "refund": {
                        "routingNumber": "021000021",
                        "isCheckingAccount": true,
                        "isSavingsAccount": true
                    }
                }
                """;

        ValidationResult result = validator.validate(json);
        assertTrue(result.hasErrors());
        assertTrue(result.getErrors().stream().anyMatch(e -> "ERR_ACCOUNT_TYPE_CONFLICT".equals(e.errorCode())));
    }

    @Test
    @DisplayName("Should detect multiple selected filing statuses")
    void testMultipleFilingStatuses() {
        String json = """
                {
                    "taxpayer": { "ssn": "123456789" },
                    "filingStatus": {
                        "single": true,
                        "marriedFilingJointly": true
                    }
                }
                """;

        ValidationResult result = validator.validate(json);
        assertTrue(result.hasErrors());
        assertTrue(result.getErrors().stream().anyMatch(e -> "ERR_MULTIPLE_FILING_STATUSES".equals(e.errorCode())));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "2026-13-01", // Invalid month 13
            "2026-04-32", // Invalid day 32
            "not-a-date"  // Malformed string
    })
    @DisplayName("Should reject malformed or illogical dates")
    void testInvalidDates(String dateStr) {
        ValidationResult result = new ValidationResult();
        assertFalse(validator.validateDateFormat("test.date", dateStr, result));
        assertTrue(result.hasErrors());
    }
}
