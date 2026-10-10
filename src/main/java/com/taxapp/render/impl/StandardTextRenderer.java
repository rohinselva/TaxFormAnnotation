package com.taxapp.render.impl;

import com.taxapp.model.FieldAnnotation;
import com.taxapp.model.FormatType;
import com.taxapp.model.TextAlignment;
import com.taxapp.render.FieldRenderer;
import com.taxapp.render.RenderContext;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;

import java.io.IOException;

/**
 * Renders standard text fields, applying adaptive font auto-shrinking and alignment calculations.
 */
public class StandardTextRenderer implements FieldRenderer {

    private static final float DEFAULT_FONT_SIZE = 8.5f;
    private static final float MIN_FONT_SIZE = 5.5f;
    private static final float BASELINE_OFFSET_Y = 2.5f;
    private static final float EDGE_PADDING_X = 2.0f;

    @Override
    public boolean supports(FormatType formatType) {
        return formatType != FormatType.CHECKBOX_BOOLEAN && formatType != FormatType.SSN_COMBED;
    }

    @Override
    public void render(RenderContext context, FieldAnnotation field, String cleanValue) throws IOException {
        if (cleanValue == null || cleanValue.isEmpty()) {
            return;
        }

        PDType1Font selectedFont = context.fontRegular();
        float fontSize = DEFAULT_FONT_SIZE;

        // Dynamic auto-shrinking: prevent text overflowing box width
        if (field.position().width() > 10.0) {
            float maxAllowedWidth = (float) (field.position().width() - 4.0);
            while (fontSize > MIN_FONT_SIZE
                    && (selectedFont.getStringWidth(cleanValue) / 1000.0f) * fontSize > maxAllowedWidth) {
                fontSize -= 0.5f;
            }
        }

        // Calculate physical string width
        float textWidth = (selectedFont.getStringWidth(cleanValue) / 1000.0f) * fontSize;

        float startX = (float) field.position().x();
        float startY = (float) field.position().y() + BASELINE_OFFSET_Y;

        // Alignment math
        if (field.format() == FormatType.CURRENCY_ROUNDED 
                || field.format() == FormatType.CURRENCY_EXACT
                || field.alignment() == TextAlignment.RIGHT) {
            float rightEdge = startX + (float) field.position().width();
            startX = rightEdge - textWidth - EDGE_PADDING_X;
        } else if (field.alignment() == TextAlignment.CENTER) {
            startX = startX + ((float) field.position().width() - textWidth) / 2.0f;
        } else {
            startX += EDGE_PADDING_X;
        }

        try (PDPageContentStream stream = new PDPageContentStream(
                context.document(),
                context.page(),
                PDPageContentStream.AppendMode.APPEND,
                true,
                true)) {
            stream.beginText();
            stream.setFont(selectedFont, fontSize);
            stream.newLineAtOffset(startX, startY);
            stream.showText(cleanValue);
            stream.endText();
        }
    }
}
