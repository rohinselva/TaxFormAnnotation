package com.taxapp.formatter.impl;

import com.taxapp.formatter.FieldFormatter;
import com.taxapp.model.FormatType;

import java.util.Locale;

/**
 * Preserves dollars and cents (e.g. 4320.75) for precise wage, credit, and deduction fields.
 */
public class CurrencyExactFormatter implements FieldFormatter {

    @Override
    public FormatType getFormatType() {
        return FormatType.CURRENCY_EXACT;
    }

    @Override
    public String format(Object rawValue) {
        if (rawValue == null) {
            return "";
        }
        if (rawValue instanceof Number num) {
            return String.format(Locale.US, "%.2f", num.doubleValue());
        }
        return rawValue.toString().replaceAll("[$,]", "").trim();
    }
}
