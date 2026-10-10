package com.taxapp.render;

import com.taxapp.model.FieldAnnotation;
import com.taxapp.model.FormatType;
import com.taxapp.model.Position;
import com.taxapp.model.TextAlignment;
import com.taxapp.render.impl.CheckboxRenderer;
import com.taxapp.render.impl.CombedBoxRenderer;
import com.taxapp.render.impl.StandardTextRenderer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RendererFactoryTest {

    private RendererFactory factory;

    @BeforeEach
    void setUp() {
        factory = new RendererFactory();
    }

    @Test
    @DisplayName("Should resolve correct renderer based on FormatType")
    void testRendererResolution() {
        assertInstanceOf(CheckboxRenderer.class, factory.getRenderer(FormatType.CHECKBOX_BOOLEAN));
        assertInstanceOf(CombedBoxRenderer.class, factory.getRenderer(FormatType.SSN_COMBED));
        assertInstanceOf(StandardTextRenderer.class, factory.getRenderer(FormatType.UPPERCASE_TEXT));
        assertInstanceOf(StandardTextRenderer.class, factory.getRenderer(FormatType.CURRENCY_ROUNDED));
        assertInstanceOf(StandardTextRenderer.class, factory.getRenderer(FormatType.CURRENCY_EXACT));
        assertInstanceOf(StandardTextRenderer.class, factory.getRenderer(FormatType.DATE_MMDDYYYY));
    }

    @Test
    @DisplayName("Should allow registering custom renderer with higher priority")
    void testCustomRendererRegistration() {
        FieldRenderer customRenderer = new FieldRenderer() {
            @Override
            public boolean supports(FormatType formatType) {
                return formatType == FormatType.UPPERCASE_TEXT;
            }

            @Override
            public void render(RenderContext context, FieldAnnotation field, String value) {
                // custom implementation
            }
        };

        factory.registerRenderer(customRenderer);
        assertSame(customRenderer, factory.getRenderer(FormatType.UPPERCASE_TEXT));
    }

    @Test
    @DisplayName("Should successfully execute rendering on blank page without errors")
    void testRenderExecution() throws Exception {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage();
            doc.addPage(page);

            PDType1Font fontRegular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            PDType1Font fontDingbats = new PDType1Font(Standard14Fonts.FontName.ZAPF_DINGBATS);
            RenderContext ctx = new RenderContext(doc, page, fontRegular, fontDingbats);

            // 1. Test StandardTextRenderer
            FieldAnnotation textField = new FieldAnnotation("name", "1", 1, new Position(100, 700, 150, 14),
                    FormatType.UPPERCASE_TEXT, TextAlignment.LEFT, "$.name");
            factory.getRenderer(FormatType.UPPERCASE_TEXT).render(ctx, textField, "JOHN DOE");

            // 2. Test CheckboxRenderer
            FieldAnnotation checkField = new FieldAnnotation("check", "2", 1, new Position(50, 700, 10, 10),
                    FormatType.CHECKBOX_BOOLEAN, TextAlignment.CENTER, "$.check");
            factory.getRenderer(FormatType.CHECKBOX_BOOLEAN).render(ctx, checkField, "4");

            // 3. Test CombedBoxRenderer
            FieldAnnotation ssnField = new FieldAnnotation("ssn", "3", 1, new Position(200, 700, 100, 14),
                    FormatType.SSN_COMBED, TextAlignment.LEFT, "$.ssn");
            factory.getRenderer(FormatType.SSN_COMBED).render(ctx, ssnField, "123456789");

            assertEquals(1, doc.getNumberOfPages());
        }
    }
}
