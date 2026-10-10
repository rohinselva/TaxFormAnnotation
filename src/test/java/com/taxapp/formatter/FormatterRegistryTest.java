package com.taxapp.formatter;

import com.taxapp.model.FormatType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

class FormatterRegistryTest {

    private FormatterRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new FormatterRegistry();
    }

    @Test
    @DisplayName("Should correctly format uppercase text")
    void testUppercaseFormatter() {
        assertEquals("JOHN DOE", registry.format(FormatType.UPPERCASE_TEXT, "john doe"));
        assertEquals("SAN FRANCISCO", registry.format(FormatType.UPPERCASE_TEXT, "  San Francisco  "));
        assertEquals("", registry.format(FormatType.UPPERCASE_TEXT, null));
    }

    @ParameterizedTest
    @CsvSource({
            "115000.50, 115001",
            "115000.49, 115000",
            "'$12,345.67', 12346",
            "500, 500"
    })
    @DisplayName("Should round currency values to nearest integer")
    void testCurrencyRounded(String input, String expected) {
        assertEquals(expected, registry.format(FormatType.CURRENCY_ROUNDED, input));
    }

    @Test
    @DisplayName("Should preserve exact cents for CURRENCY_EXACT")
    void testCurrencyExact() {
        assertEquals("4320.75", registry.format(FormatType.CURRENCY_EXACT, 4320.75));
        assertEquals("100.00", registry.format(FormatType.CURRENCY_EXACT, 100.0));
        assertEquals("5250.80", registry.format(FormatType.CURRENCY_EXACT, "5250.80"));
    }

    @Test
    @DisplayName("Should map boolean checkboxes to ZapfDingbats checkmark glyph '4'")
    void testCheckboxBoolean() {
        assertEquals("4", registry.format(FormatType.CHECKBOX_BOOLEAN, true));
        assertEquals("4", registry.format(FormatType.CHECKBOX_BOOLEAN, "true"));
        assertEquals("4", registry.format(FormatType.CHECKBOX_BOOLEAN, "yes"));
        assertEquals("", registry.format(FormatType.CHECKBOX_BOOLEAN, false));
        assertEquals("", registry.format(FormatType.CHECKBOX_BOOLEAN, null));
    }

    @Test
    @DisplayName("Should strip non-digits for SSN_COMBED and SSN_UNMASKED")
    void testSsnCombedAndUnmasked() {
        assertEquals("123456789", registry.format(FormatType.SSN_COMBED, "123-45-6789"));
        assertEquals("987654321", registry.format(FormatType.SSN_UNMASKED, " 987 65 4321 "));
    }

    @Test
    @DisplayName("Should mask first 5 digits for SSN_MASKED")
    void testSsnMasked() {
        assertEquals("XXXXX6789", registry.format(FormatType.SSN_MASKED, "123-45-6789"));
        assertEquals("XXXXX1234", registry.format(FormatType.SSN_MASKED, "000-00-1234"));
    }

    @Test
    @DisplayName("Should format ISO dates to MM/DD/YYYY")
    void testDateFormatter() {
        assertEquals("04/15/2026", registry.format(FormatType.DATE_MMDDYYYY, "2026-04-15"));
        assertEquals("12/31/2025", registry.format(FormatType.DATE_MMDDYYYY, "2025-12-31"));
    }

    @Test
    @DisplayName("Should allow registering custom formatters")
    void testCustomFormatterRegistration() {
        FieldFormatter customFormatter = new FieldFormatter() {
            @Override
            public FormatType getFormatType() {
                return FormatType.UPPERCASE_TEXT;
            }

            @Override
            public String format(Object rawValue) {
                return "CUSTOM:" + rawValue;
            }
        };

        registry.register(customFormatter);
        assertEquals("CUSTOM:test", registry.format(FormatType.UPPERCASE_TEXT, "test"));
    }
}
