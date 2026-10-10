package com.taxapp.formatter.impl;

import com.taxapp.formatter.FieldFormatter;
import com.taxapp.model.FormatType;

/**
 * Standardizes ISO YYYY-MM-DD or raw date strings into IRS-compliant MM/DD/YYYY format.
 */
public class DateFormatter implements FieldFormatter {

    @Override
    public FormatType getFormatType() {
        return FormatType.DATE_MMDDYYYY;
    }

    @Override
    public String format(Object rawValue) {
        if (rawValue == null) {
            return "";
        }
        String str = rawValue.toString().trim();
        if (str.matches("\\d{4}-\\d{2}-\\d{2}")) {
            String[] p = str.split("-");
            return p[1] + "/" + p[2] + "/" + p[0];
        }
        return str.replaceAll("[^0-9/]", "").trim();
    }
}
