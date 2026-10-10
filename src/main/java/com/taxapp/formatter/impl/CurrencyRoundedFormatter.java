package com.taxapp.formatter.impl;

import com.taxapp.formatter.FieldFormatter;
import com.taxapp.model.FormatType;

/**
 * Rounds currency values to the nearest integer per IRS tax table specifications.
 */
public class CurrencyRoundedFormatter implements FieldFormatter {

    @Override
    public FormatType getFormatType() {
        return FormatType.CURRENCY_ROUNDED;
    }

    @Override
    public String format(Object rawValue) {
        if (rawValue == null) {
            return "";
        }
        if (rawValue instanceof Number num) {
            return String.valueOf(Math.round(num.doubleValue()));
        }
        String cleaned = rawValue.toString().replaceAll("[$,]", "").trim();
        if (cleaned.isEmpty()) {
            return "";
        }
        try {
            double parsed = Double.parseDouble(cleaned);
            return String.valueOf(Math.round(parsed));
        } catch (NumberFormatException e) {
            return cleaned.split("\\.")[0];
        }
    }
}
