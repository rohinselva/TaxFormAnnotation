package com.taxapp.render.impl;

import com.taxapp.model.FieldAnnotation;
import com.taxapp.model.FormatType;
import com.taxapp.render.FieldRenderer;
import com.taxapp.render.RenderContext;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;

import java.io.IOException;

/**
 * Renders checkbox checkmark glyphs using standard ZapfDingbats font.
 */
public class CheckboxRenderer implements FieldRenderer {

    private static final float CHECKBOX_FONT_SIZE = 8.5f;
    private static final float CHECKBOX_BASELINE_OFFSET_Y = 1.0f;

    @Override
    public boolean supports(FormatType formatType) {
        return formatType == FormatType.CHECKBOX_BOOLEAN;
    }

    @Override
    public void render(RenderContext context, FieldAnnotation field, String cleanValue) throws IOException {
        if (cleanValue == null || cleanValue.isEmpty()) {
            return;
        }

        PDType1Font selectedFont = context.fontDingbats();
        float fontSize = CHECKBOX_FONT_SIZE;

        // If normalized character is '4' or 'X', convert to checkmark character
        String glyph = cleanValue;
        if ("4".equals(glyph) || "X".equalsIgnoreCase(glyph)) {
            glyph = "\u2713";
        }

        float textWidth = (selectedFont.getStringWidth(glyph) / 1000.0f) * fontSize;
        float startX = (float) field.position().x() + ((float) field.position().width() - textWidth) / 2.0f;
        float startY = (float) field.position().y() + CHECKBOX_BASELINE_OFFSET_Y;

        try (PDPageContentStream stream = new PDPageContentStream(
                context.document(),
                context.page(),
                PDPageContentStream.AppendMode.APPEND,
                true,
                true)) {
            stream.beginText();
            stream.setFont(selectedFont, fontSize);
            stream.newLineAtOffset(startX, startY);
            stream.showText(glyph);
            stream.endText();
        }
    }
}
