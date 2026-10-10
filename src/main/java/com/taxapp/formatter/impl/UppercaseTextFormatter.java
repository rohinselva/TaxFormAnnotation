package com.taxapp.formatter.impl;

import com.taxapp.formatter.FieldFormatter;
import com.taxapp.model.FormatType;

/**
 * Formats strings to uppercase and trims whitespace.
 */
public class UppercaseTextFormatter implements FieldFormatter {

    @Override
    public FormatType getFormatType() {
        return FormatType.UPPERCASE_TEXT;
    }

    @Override
    public String format(Object rawValue) {
        if (rawValue == null) {
            return "";
        }
        return rawValue.toString().toUpperCase().trim();
    }
}
