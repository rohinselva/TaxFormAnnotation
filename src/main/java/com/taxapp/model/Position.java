package com.taxapp.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Represents the bounding box coordinates of a field on a PDF page in points (1/72 inch).
 * The origin (0,0) is at the bottom-left corner of the page.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Position(
    double x,
    double y,
    double width,
    double height
) {}
