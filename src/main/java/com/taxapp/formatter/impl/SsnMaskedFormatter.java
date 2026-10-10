package com.taxapp.formatter.impl;

import com.taxapp.formatter.FieldFormatter;
import com.taxapp.model.FormatType;

/**
 * Masks the first 5 digits of an SSN with 'XXXXX' while leaving the final 4 digits visible for client copies.
 */
public class SsnMaskedFormatter implements FieldFormatter {

    @Override
    public FormatType getFormatType() {
        return FormatType.SSN_MASKED;
    }

    @Override
    public String format(Object rawValue) {
        if (rawValue == null) {
            return "";
        }
        String clean = rawValue.toString().replaceAll("[^0-9]", "").trim();
        if (clean.length() == 9) {
            return "XXXXX" + clean.substring(5);
        }
        return clean;
    }
}
