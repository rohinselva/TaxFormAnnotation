package com.taxapp.render;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.font.PDType1Font;

/**
 * Context object holding graphics resources and document references for rendering a field.
 */
public record RenderContext(
    PDDocument document,
    PDPage page,
    PDType1Font fontRegular,
    PDType1Font fontDingbats
) {
}
