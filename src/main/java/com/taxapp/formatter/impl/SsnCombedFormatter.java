package com.taxapp.formatter.impl;

import com.taxapp.formatter.FieldFormatter;
import com.taxapp.model.FormatType;

/**
 * Strips hyphens, spaces, and non-numeric characters from SSNs destined for combed partitioned boxes.
 */
public class SsnCombedFormatter implements FieldFormatter {

    @Override
    public FormatType getFormatType() {
        return FormatType.SSN_COMBED;
    }

    @Override
    public String format(Object rawValue) {
        if (rawValue == null) {
            return "";
        }
        return rawValue.toString().replaceAll("[^0-9]", "").trim();
    }
}
