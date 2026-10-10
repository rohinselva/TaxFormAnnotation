package com.taxapp.render;

import com.taxapp.model.FieldAnnotation;
import com.taxapp.model.FormatType;

import java.io.IOException;

/**
 * Strategy interface for rendering formatted tax values onto a PDF page content stream.
 */
public interface FieldRenderer {

    /**
     * Determines whether this renderer handles the specified format type.
     */
    boolean supports(FormatType formatType);

    /**
     * Stamps the formatted string onto the PDF canvas according to coordinate and font geometry.
     *
     * @param context Graphics context containing the active document, page, and fonts
     * @param field Field coordinate and alignment specification
     * @param value Cleaned and formatted string to render
     * @throws IOException If PDF stream operations fail
     */
    void render(RenderContext context, FieldAnnotation field, String value) throws IOException;
}
