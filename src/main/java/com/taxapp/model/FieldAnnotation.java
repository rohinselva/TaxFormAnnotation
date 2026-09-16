package com.taxapp.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents the mapping specification for an individual field on Form 1040.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record FieldAnnotation(
    @JsonProperty("fieldId") String fieldId,
    @JsonProperty("irsLineNumber") String irsLineNumber,
    @JsonProperty("page") int page,
    @JsonProperty("position") Position position,
    @JsonProperty("format") FormatType format,
    @JsonProperty("alignment") TextAlignment alignment,
    @JsonProperty("dataPath") String dataPath
) {
    public FieldAnnotation {
        if (alignment == null) {
            if (format == FormatType.CURRENCY_ROUNDED || format == FormatType.CURRENCY_EXACT) {
                alignment = TextAlignment.RIGHT;
            } else if (format == FormatType.CHECKBOX_BOOLEAN) {
                alignment = TextAlignment.CENTER;
            } else {
                alignment = TextAlignment.LEFT;
            }
        }
    }
}
