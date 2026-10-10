package com.taxapp.formatter.impl;

import com.taxapp.formatter.FieldFormatter;
import com.taxapp.model.FormatType;

/**
 * Standard unmasked 9-digit SSN formatter removing hyphens and non-numeric characters.
 */
public class SsnUnmaskedFormatter implements FieldFormatter {

    @Override
    public FormatType getFormatType() {
        return FormatType.SSN_UNMASKED;
    }

    @Override
    public String format(Object rawValue) {
        if (rawValue == null) {
            return "";
        }
        return rawValue.toString().replaceAll("[^0-9]", "").trim();
    }
}
