package com.taxapp.formatter;

import com.taxapp.formatter.impl.CheckboxBooleanFormatter;
import com.taxapp.formatter.impl.CurrencyExactFormatter;
import com.taxapp.formatter.impl.CurrencyRoundedFormatter;
import com.taxapp.formatter.impl.DateFormatter;
import com.taxapp.formatter.impl.SsnCombedFormatter;
import com.taxapp.formatter.impl.SsnMaskedFormatter;
import com.taxapp.formatter.impl.SsnUnmaskedFormatter;
import com.taxapp.formatter.impl.UppercaseTextFormatter;
import com.taxapp.model.FormatType;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * Central registry and factory for FieldFormatter strategies.
 */
public class FormatterRegistry {

    private final Map<FormatType, FieldFormatter> formatters = new EnumMap<>(FormatType.class);
    private final FieldFormatter defaultFormatter = new UppercaseTextFormatter();

    public FormatterRegistry() {
        registerDefaultFormatters();
    }

    private void registerDefaultFormatters() {
        register(new UppercaseTextFormatter());
        register(new CurrencyRoundedFormatter());
        register(new CurrencyExactFormatter());
        register(new CheckboxBooleanFormatter());
        register(new SsnCombedFormatter());
        register(new SsnUnmaskedFormatter());
        register(new SsnMaskedFormatter());
        register(new DateFormatter());
    }

    /**
     * Registers or overrides a FieldFormatter strategy.
     *
     * @param formatter Strategy implementation to register
     */
    public void register(FieldFormatter formatter) {
        Objects.requireNonNull(formatter, "Formatter cannot be null");
        Objects.requireNonNull(formatter.getFormatType(), "Formatter FormatType cannot be null");
        formatters.put(formatter.getFormatType(), formatter);
    }

    /**
     * Retrieves the formatter registered for the given FormatType.
     *
     * @param formatType Target format type
     * @return Registered FieldFormatter, or fallback defaultFormatter if unmapped
     */
    public FieldFormatter getFormatter(FormatType formatType) {
        if (formatType == null) {
            return defaultFormatter;
        }
        return formatters.getOrDefault(formatType, defaultFormatter);
    }

    /**
     * Convenience method to format a value directly via registered strategy.
     */
    public String format(FormatType formatType, Object rawValue) {
        return getFormatter(formatType).format(rawValue);
    }
}
