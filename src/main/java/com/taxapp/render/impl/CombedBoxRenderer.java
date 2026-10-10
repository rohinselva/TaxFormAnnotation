package com.taxapp.render.impl;

import com.taxapp.model.FieldAnnotation;
import com.taxapp.model.FormatType;
import com.taxapp.render.FieldRenderer;
import com.taxapp.render.RenderContext;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;

import java.io.IOException;

/**
 * Renders partitioned combed boxes (such as IRS SSN containers with dashed visual separators).
 * Splits 9-digit SSNs into physical sub-segments (3, 2, and 4 digits) positioned at calibrated physical ratios.
 */
public class CombedBoxRenderer implements FieldRenderer {

    private static final float COMB_FONT_SIZE = 8.5f;
    private static final float COMB_BASELINE_OFFSET_Y = 2.5f;

    @Override
    public boolean supports(FormatType formatType) {
        return formatType == FormatType.SSN_COMBED;
    }

    @Override
    public void render(RenderContext context, FieldAnnotation field, String cleanValue) throws IOException {
        if (cleanValue == null || cleanValue.isEmpty()) {
            return;
        }

        String cleanSSN = cleanValue.replaceAll("[^0-9]", "");
        if (cleanSSN.length() != 9) {
            return;
        }

        String part1 = cleanSSN.substring(0, 3);
        String part2 = cleanSSN.substring(3, 5);
        String part3 = cleanSSN.substring(5, 9);

        float startX = (float) field.position().x();
        float startY = (float) field.position().y() + COMB_BASELINE_OFFSET_Y;
        float boxWidth = (float) field.position().width();

        // The IRS SSN box has dashed lines at roughly 33% and 55%.
        // We calculate exact X starting points for each of the 3 segments.
        float xOffset1 = startX + 3.0f;
        float xOffset2 = startX + (boxWidth * 0.36f);
        float xOffset3 = startX + (boxWidth * 0.60f);

        PDType1Font font = context.fontRegular();

        try (PDPageContentStream stream = new PDPageContentStream(
                context.document(),
                context.page(),
                PDPageContentStream.AppendMode.APPEND,
                true,
                true)) {

            // Draw Part 1 (XXX)
            stream.beginText();
            stream.setFont(font, COMB_FONT_SIZE);
            stream.newLineAtOffset(xOffset1, startY);
            stream.showText(part1);
            stream.endText();

            // Draw Part 2 (XX)
            stream.beginText();
            stream.setFont(font, COMB_FONT_SIZE);
            stream.newLineAtOffset(xOffset2, startY);
            stream.showText(part2);
            stream.endText();

            // Draw Part 3 (XXXX)
            stream.beginText();
            stream.setFont(font, COMB_FONT_SIZE);
            stream.newLineAtOffset(xOffset3, startY);
            stream.showText(part3);
            stream.endText();
        }
    }
}
