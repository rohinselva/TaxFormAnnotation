package com.taxapp.formatter.impl;

import com.taxapp.formatter.FieldFormatter;
import com.taxapp.model.FormatType;

/**
 * Normalizes boolean checkboxes into ZapfDingbats checkmark glyphs (character '4' maps to checkmark).
 */
public class CheckboxBooleanFormatter implements FieldFormatter {

    @Override
    public FormatType getFormatType() {
        return FormatType.CHECKBOX_BOOLEAN;
    }

    @Override
    public String format(Object rawValue) {
        if (rawValue == null) {
            return "";
        }
        if (rawValue instanceof Boolean bool && Boolean.TRUE.equals(bool)) {
            return "4";
        }
        if (rawValue instanceof String str && ("true".equalsIgnoreCase(str) || "yes".equalsIgnoreCase(str) || "1".equals(str))) {
            return "4";
        }
        return "";
    }
}
