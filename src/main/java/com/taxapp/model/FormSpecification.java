package com.taxapp.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * Encapsulates the entire form annotation specification for a specific tax year and form revision.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record FormSpecification(
    String formId,
    int taxYear,
    int pages,
    String unit,
    PageDimensions pageDimensions,
    List<FieldAnnotation> fields
) {}
