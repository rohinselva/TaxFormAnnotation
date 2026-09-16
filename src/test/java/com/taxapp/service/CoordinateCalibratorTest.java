package com.taxapp.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;

public class CoordinateCalibratorTest {

    @Test
    void generateRulerGrid() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/f1040_blank.pdf");
                PDDocument doc = Loader.loadPDF(in.readAllBytes())) {

            PDPage page = doc.getPage(1); // Page 2
            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

            try (PDPageContentStream stream = new PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND,
                    true, true)) {
                stream.setFont(font, 5.0f);

                // Draw vertical grid lines every 25 points
                for (float x = 25; x < 612; x += 25) {
                    stream.setStrokingColor(0.85f, 0.85f, 1.0f); // faint blue
                    stream.moveTo(x, 0);
                    stream.lineTo(x, 792);
                    stream.stroke();

                    // Print X coordinate label every 50 points
                    if (x % 50 == 0) {
                        stream.setNonStrokingColor(0.2f, 0.2f, 0.9f);
                        stream.beginText();
                        stream.newLineAtOffset(x + 1, 780);
                        stream.showText(String.valueOf((int) x));
                        stream.endText();

                        stream.beginText();
                        stream.newLineAtOffset(x + 1, 10);
                        stream.showText(String.valueOf((int) x));
                        stream.endText();
                    }
                }

                // Draw horizontal grid lines every 25 points
                for (float y = 25; y < 792; y += 25) {
                    stream.setStrokingColor(1.0f, 0.85f, 0.85f); // faint red
                    stream.moveTo(0, y);
                    stream.lineTo(612, y);
                    stream.stroke();

                    // Print Y coordinate label every 50 points
                    if (y % 50 == 0) {
                        stream.setNonStrokingColor(0.9f, 0.2f, 0.2f);
                        stream.beginText();
                        stream.newLineAtOffset(5, y + 1);
                        stream.showText(String.valueOf((int) y));
                        stream.endText();

                        stream.beginText();
                        stream.newLineAtOffset(585, y + 1);
                        stream.showText(String.valueOf((int) y));
                        stream.endText();
                    }
                }
            }

            PDFRenderer renderer = new PDFRenderer(doc);
            BufferedImage bim = renderer.renderImageWithDPI(1, 150);
            File gridPreview = new File(
                    "C:/Users/rohin/.gemini/antigravity-ide/brain/99f0db0d-cb2e-4cd0-abe1-7f9c23f05038/form1040_grid_ruler_page2.png");
            ImageIO.write(bim, "PNG", gridPreview);
            System.out.println("Ruler grid preview written to: " + gridPreview.getAbsolutePath());
        }
    }

    @Test
    void renderSsnBoxCrop() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/f1040_blank.pdf");
                PDDocument doc = Loader.loadPDF(in.readAllBytes())) {
            PDFRenderer renderer = new PDFRenderer(doc);
            BufferedImage bim = renderer.renderImageWithDPI(0, 300); // high res
            int cropX = (int) (400 * (300f / 72f));
            int cropY = (int) ((792 - 710) * (300f / 72f));
            int cropW = (int) (200 * (300f / 72f));
            int cropH = (int) (60 * (300f / 72f));
            BufferedImage ssnCrop = bim.getSubimage(cropX, cropY, cropW, cropH);
            ImageIO.write(ssnCrop, "PNG", new File(
                    "C:/Users/rohin/.gemini/antigravity-ide/brain/99f0db0d-cb2e-4cd0-abe1-7f9c23f05038/ssn_crop.png"));
        }
    }

    @Test
    void testSsnSpacingOptions() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/f1040_blank.pdf");
                PDDocument doc = Loader.loadPDF(in.readAllBytes())) {
            PDPage page = doc.getPage(0);
            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            float fontSize = 8.5f;

            // Let's test 3 different spacings on 3 blank SSN fields:
            // taxpayer SSN (y=684): with 3 spaces
            // spouse SSN (y=660): with 4 spaces
            // dep 1 SSN (y=447): with 5 spaces
            try (PDPageContentStream stream = new PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND,
                    true, true)) {
                // Row 1 (y=471): user offset (0.34f, 0.58f)
                float boxW = 106.25f;
                float startX = 145.0f;
                float startY1 = 471.0f + 2.5f;
                stream.beginText();
                stream.setFont(font, fontSize);
                stream.newLineAtOffset(startX + 2.0f, startY1);
                stream.showText("987");
                stream.endText();
                stream.beginText();
                stream.setFont(font, fontSize);
                stream.newLineAtOffset(startX + boxW * 0.34f, startY1);
                stream.showText("65");
                stream.endText();
                stream.beginText();
                stream.setFont(font, fontSize);
                stream.newLineAtOffset(startX + boxW * 0.58f, startY1);
                stream.showText("4321");
                stream.endText();

                // Row 2 (y=459): slight nudge (0.36f, 0.59f)
                float startY2 = 459.0f + 2.5f;
                stream.beginText();
                stream.setFont(font, fontSize);
                stream.newLineAtOffset(startX + 3.0f, startY2);
                stream.showText("987");
                stream.endText();
                stream.beginText();
                stream.setFont(font, fontSize);
                stream.newLineAtOffset(startX + boxW * 0.36f, startY2);
                stream.showText("65");
                stream.endText();
                stream.beginText();
                stream.setFont(font, fontSize);
                stream.newLineAtOffset(startX + boxW * 0.59f, startY2);
                stream.showText("4321");
                stream.endText();

                // Row 3 (y=447): centered in compartments
                // Comp 1: [0, 35.24], textW=14.2 -> x = 145 + (35.24-14.2)/2 = 145 + 10.5
                // Comp 2: [35.24, 59.48], width=24.24, textW=9.5 -> x = 145 + 35.24 +
                // (24.24-9.5)/2 = 145 + 42.6
                // Comp 3: [59.48, 106.25], width=46.77, textW=19.0 -> x = 145 + 59.48 +
                // (46.77-19.0)/2 = 145 + 73.3
                float startY3 = 447.0f + 2.5f;
                stream.beginText();
                stream.setFont(font, fontSize);
                stream.newLineAtOffset(startX + 6.0f, startY3);
                stream.showText("987");
                stream.endText();
                stream.beginText();
                stream.setFont(font, fontSize);
                stream.newLineAtOffset(startX + 39.0f, startY3);
                stream.showText("65");
                stream.endText();
                stream.beginText();
                stream.setFont(font, fontSize);
                stream.newLineAtOffset(startX + 64.0f, startY3);
                stream.showText("4321");
                stream.endText();
            }

            PDFRenderer renderer = new PDFRenderer(doc);
            BufferedImage bim = renderer.renderImageWithDPI(0, 300);
            int cropX = (int) (400 * (300f / 72f));
            int cropY = (int) ((792 - 710) * (300f / 72f));
            int cropW = (int) (200 * (300f / 72f));
            int cropH = (int) (60 * (300f / 72f));
            BufferedImage ssnCrop = bim.getSubimage(cropX, cropY, cropW, cropH);
            ImageIO.write(ssnCrop, "PNG", new File(
                    "C:/Users/rohin/.gemini/antigravity-ide/brain/99f0db0d-cb2e-4cd0-abe1-7f9c23f05038/ssn_spacing_test.png"));

            int depCropX = (int) (130 * (300f / 72f));
            int depCropY = (int) ((792 - 485) * (300f / 72f));
            int depCropW = (int) (250 * (300f / 72f));
            int depCropH = (int) (65 * (300f / 72f));
            BufferedImage depCrop = bim.getSubimage(depCropX, depCropY, depCropW, depCropH);
            ImageIO.write(depCrop, "PNG", new File(
                    "C:/Users/rohin/.gemini/antigravity-ide/brain/99f0db0d-cb2e-4cd0-abe1-7f9c23f05038/dep_spacing_test.png"));
            System.out.println("Spacing test images saved.");
        }
    }

    @Test
    void detectDashedLines() throws Exception {
        InputStream blankPdfStream = getClass().getResourceAsStream("/f1040_blank.pdf");
        try (PDDocument doc = Loader.loadPDF(blankPdfStream.readAllBytes())) {
            PDFRenderer renderer = new PDFRenderer(doc);
            BufferedImage bim = renderer.renderImageWithDPI(0, 300);
            float scale = 300f / 72f;

            // 1. Header SSN (x=469, y=684, w=107, h=14)
            System.out.println("--- Header SSN dashed lines ---");
            int ssnY_px = (int) ((792 - 684 - 7) * scale); // mid-height
            int ssnStartX_px = (int) (469 * scale);
            int ssnEndX_px = (int) ((469 + 107) * scale);
            for (int x = ssnStartX_px; x < ssnEndX_px; x++) {
                // Check if dashed line has dark pixel at mid height
                int rgb = bim.getRGB(x, ssnY_px);
                int r = (rgb >> 16) & 0xFF;
                if (r < 100) {
                    float ptX = x / scale;
                    System.out.printf("SSN dashed at ptX=%.2f (offset=%.2f, ratio=%.3f)%n", ptX, ptX - 469.0f,
                            (ptX - 469.0f) / 107.0f);
                }
            }

            // 2. Dependent 1 SSN (x=145, y=447, w=106.25, h=12)
            System.out.println("--- Dependent 1 SSN dashed lines ---");
            int depStartX_px = (int) (145 * scale);
            int depEndX_px = (int) ((145 + 106.25) * scale);
            for (int x = depStartX_px; x < depEndX_px; x++) {
                int darkCount = 0;
                for (int y = (int) ((792 - 457) * scale); y < (int) ((792 - 447) * scale); y++) {
                    int rgb = bim.getRGB(x, y);
                    int r = (rgb >> 16) & 0xFF;
                    if (r < 100)
                        darkCount++;
                }
                if (darkCount >= 5) {
                    float ptX = x / scale;
                    System.out.printf("Dep dashed at ptX=%.2f (offset=%.2f, ratio=%.3f, dark=%d)%n", ptX, ptX - 145.0f,
                            (ptX - 145.0f) / 106.25f, darkCount);
                }
            }
        }
    }

    @Test
    void cropFilledForm() throws Exception {
        File pdfFile = new File("Form1040_Filled_2025.pdf");
        if (!pdfFile.exists())
            return;
        try (PDDocument doc = Loader.loadPDF(pdfFile)) {
            PDFRenderer renderer = new PDFRenderer(doc);
            BufferedImage bim = renderer.renderImageWithDPI(0, 300);

            // SSN Header crop
            int cropX = (int) (400 * (300f / 72f));
            int cropY = (int) ((792 - 710) * (300f / 72f));
            int cropW = (int) (200 * (300f / 72f));
            int cropH = (int) (60 * (300f / 72f));
            BufferedImage ssnCrop = bim.getSubimage(cropX, cropY, cropW, cropH);
            ImageIO.write(ssnCrop, "PNG", new File(
                    "C:/Users/rohin/.gemini/antigravity-ide/brain/99f0db0d-cb2e-4cd0-abe1-7f9c23f05038/ssn_filled_crop.png"));

            // Dependents crop
            int depCropX = (int) (130 * (300f / 72f));
            int depCropY = (int) ((792 - 485) * (300f / 72f));
            int depCropW = (int) (250 * (300f / 72f));
            int depCropH = (int) (65 * (300f / 72f));
            BufferedImage depCrop = bim.getSubimage(depCropX, depCropY, depCropW, depCropH);
            ImageIO.write(depCrop, "PNG", new File(
                    "C:/Users/rohin/.gemini/antigravity-ide/brain/99f0db0d-cb2e-4cd0-abe1-7f9c23f05038/dep_filled_crop.png"));

            // Checkbox crop (Filing Status Single)
            float scale = 300f / 72f;
            int cbCropX = (int) (85 * scale);
            int cbCropY = (int) ((792 - 590) * scale);
            int cbCropW = (int) (120 * scale);
            int cbCropH = (int) (35 * scale);
            BufferedImage cbCrop = bim.getSubimage(cbCropX, cbCropY, cbCropW, cbCropH);
            ImageIO.write(cbCrop, "PNG", new File(
                    "C:/Users/rohin/.gemini/antigravity-ide/brain/99f0db0d-cb2e-4cd0-abe1-7f9c23f05038/checkbox_filled_crop.png"));

            System.out.println("Filled crops updated.");
        }
    }

    @Test
    void testDingbatsCheckmark() throws Exception {
        InputStream blankPdfStream = getClass().getResourceAsStream("/f1040_blank.pdf");
        try (PDDocument doc = Loader.loadPDF(blankPdfStream.readAllBytes())) {
            PDPage page = doc.getPage(0);
            PDType1Font fontDingbats = new PDType1Font(Standard14Fonts.FontName.ZAPF_DINGBATS);
            byte[] b1 = fontDingbats.encode("\u2713");
            byte[] b2 = fontDingbats.encode("\u2714");
            System.out.printf("\\u2713 encoded to: 0x%02X (char '%c')%n", b1[0], (char) b1[0]);
            System.out.printf("\\u2714 encoded to: 0x%02X (char '%c')%n", b2[0], (char) b2[0]);
            float size1 = 9.0f;
            float w1 = (fontDingbats.getStringWidth("\u2713") / 1000f) * size1;
            float x1 = 97.6f + (8.0f - w1) / 2.0f;
            float y1 = 578.0f + 1.0f; // test vertical baseline offset

            // Test 2: \u2714 (heavy check) at MFJ (x=97.6, y=566.0, w=8, h=8)
            float size2 = 9.0f;
            float w2 = (fontDingbats.getStringWidth("\u2714") / 1000f) * size2;
            float x2 = 97.6f + (8.0f - w2) / 2.0f;
            float y2 = 566.0f + 1.0f;

            // Test 3: \u2713 with size 8.0f at MFS (x=97.6, y=554.0, w=8, h=8)
            float size3 = 8.0f;
            float w3 = (fontDingbats.getStringWidth("\u2713") / 1000f) * size3;
            float x3 = 97.6f + (8.0f - w3) / 2.0f;
            float y3 = 554.0f + 1.5f;

            try (PDPageContentStream stream = new PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND,
                    true, true)) {
                stream.beginText();
                stream.setFont(fontDingbats, size1);
                stream.newLineAtOffset(x1, y1);
                stream.showText("\u2713");
                stream.endText();

                stream.beginText();
                stream.setFont(fontDingbats, size2);
                stream.newLineAtOffset(x2, y2);
                stream.showText("\u2714");
                stream.endText();

                stream.beginText();
                stream.setFont(fontDingbats, size3);
                stream.newLineAtOffset(x3, y3);
                stream.showText("\u2713");
                stream.endText();
            }

            PDFRenderer renderer = new PDFRenderer(doc);
            BufferedImage bim = renderer.renderImageWithDPI(0, 300);
            float scale = 300f / 72f;
            int cropX = (int) (85 * scale);
            int cropY = (int) ((792 - 590) * scale);
            int cropW = (int) (120 * scale);
            int cropH = (int) (55 * scale);
            BufferedImage crop = bim.getSubimage(cropX, cropY, cropW, cropH);
            ImageIO.write(crop, "PNG", new File(
                    "C:/Users/rohin/.gemini/antigravity-ide/brain/99f0db0d-cb2e-4cd0-abe1-7f9c23f05038/checkbox_tick_test.png"));
            System.out.println("checkbox_tick_test.png saved.");
        }
    }
}
