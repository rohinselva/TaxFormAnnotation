package com.taxapp.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Standard page dimensions in PDF points (612 x 792 for standard US Letter).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PageDimensions(
    double width,
    double height
) {}
