package com.taxapp.formatter;

import com.taxapp.model.FormatType;

/**
 * Strategy interface for formatting raw payload data into tax-compliant display values.
 */
public interface FieldFormatter {

    /**
     * @return The specific FormatType handled by this formatter.
     */
    FormatType getFormatType();

    /**
     * Formats raw JSONPath extracted values into a normalized string for canvas stamping.
     *
     * @param rawValue Object extracted from taxpayer JSON payload (can be null, String, Number, Boolean)
     * @return Formatted printable string, or empty string if value is null or absent
     */
    String format(Object rawValue);
}
