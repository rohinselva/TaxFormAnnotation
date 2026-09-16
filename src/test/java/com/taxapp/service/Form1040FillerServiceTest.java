package com.taxapp.service;

import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import com.taxapp.model.FieldAnnotation;
import com.taxapp.model.FormatType;
import com.taxapp.model.FormSpecification;
import com.taxapp.model.Position;
import com.taxapp.model.TextAlignment;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class Form1040FillerServiceTest {

    private Form1040FillerService fillerService;

    @BeforeEach
    void setUp() {
        fillerService = new Form1040FillerService();
    }

    @Test
    @DisplayName("Should successfully parse form specification JSON")
    void testLoadSpecification() throws Exception {
        try (InputStream stream = getClass().getResourceAsStream("/specs/form1040_2025_spec.json")) {
            assertNotNull(stream, "Specification file should be found on classpath");
            FormSpecification spec = fillerService.loadSpecification(stream);

            assertEquals("IRS-1040", spec.formId());
            assertEquals(2025, spec.taxYear());
            assertEquals(2, spec.pages());
            assertEquals("POINTS", spec.unit());
            assertNotNull(spec.pageDimensions());
            assertEquals(612.0, spec.pageDimensions().width());
            assertEquals(792.0, spec.pageDimensions().height());
            assertFalse(spec.fields().isEmpty(), "Fields list should not be empty");

            // Verify specific field mapping
            FieldAnnotation wagesField = spec.fields().stream()
                    .filter(f -> "line_1a_w2_wages".equals(f.fieldId()))
                    .findFirst()
                    .orElse(null);
            assertNotNull(wagesField);
            assertEquals("1a", wagesField.irsLineNumber());
            assertEquals(1, wagesField.page());
            assertEquals(FormatType.CURRENCY_ROUNDED, wagesField.format());
            assertEquals(TextAlignment.RIGHT, wagesField.alignment());
            assertEquals("$.income.w2Wages", wagesField.dataPath());
        }
    }

    @Test
    @DisplayName("Should accurately format values according to FormatType")
    void testExtractAndFormat() {
        String testJson = """
                {
                    "user": {
                        "name": "john doe",
                        "ssn": "123456789",
                        "isActive": true,
                        "wages": 115000.50,
                        "bonus": 5250.75
                    }
                }
                """;

        Configuration cfg = Configuration.defaultConfiguration()
                .addOptions(Option.DEFAULT_PATH_LEAF_TO_NULL, Option.SUPPRESS_EXCEPTIONS);
        DocumentContext ctx = JsonPath.using(cfg).parse(testJson);

        Position pos = new Position(10, 10, 100, 20);

        // UPPERCASE_TEXT
        FieldAnnotation nameField = new FieldAnnotation("name", "1", 1, pos, FormatType.UPPERCASE_TEXT,
                TextAlignment.LEFT, "$.user.name");
        assertEquals("JOHN DOE", fillerService.extractAndFormat(ctx, nameField));

        // CURRENCY_ROUNDED
        FieldAnnotation wagesField = new FieldAnnotation("wages", "2", 1, pos, FormatType.CURRENCY_ROUNDED,
                TextAlignment.RIGHT, "$.user.wages");
        assertEquals("115001", fillerService.extractAndFormat(ctx, wagesField));

        // CURRENCY_EXACT
        FieldAnnotation bonusField = new FieldAnnotation("bonus", "3", 1, pos, FormatType.CURRENCY_EXACT,
                TextAlignment.RIGHT, "$.user.bonus");
        assertEquals("5250.75", fillerService.extractAndFormat(ctx, bonusField));

        // CHECKBOX_BOOLEAN (ZapfDingbats checkmark '4')
        FieldAnnotation activeField = new FieldAnnotation("active", "4", 1, pos, FormatType.CHECKBOX_BOOLEAN,
                TextAlignment.CENTER, "$.user.isActive");
        assertEquals("4", fillerService.extractAndFormat(ctx, activeField));

        // SSN_UNMASKED (hyphens removed)
        FieldAnnotation ssnField = new FieldAnnotation("ssn", "5", 1, pos, FormatType.SSN_UNMASKED, TextAlignment.LEFT,
                "$.user.ssn");
        assertEquals("123456789", fillerService.extractAndFormat(ctx, ssnField));

        // SSN_COMBED (hyphens removed)
        FieldAnnotation combedSsnField = new FieldAnnotation("ssn_c", "5b", 1, pos, FormatType.SSN_COMBED,
                TextAlignment.LEFT, "$.user.ssn");
        assertEquals("123456789", fillerService.extractAndFormat(ctx, combedSsnField));

        // SSN_MASKED
        FieldAnnotation maskedSsnField = new FieldAnnotation("ssn_m", "6", 1, pos, FormatType.SSN_MASKED,
                TextAlignment.LEFT, "$.user.ssn");
        assertEquals("XXXXX6789", fillerService.extractAndFormat(ctx, maskedSsnField));
    }

    @Test
    @DisplayName("Should generate filled PDF without errors and preserve valid PDF structure")
    void testGenerateFilledPdf(@TempDir Path tempDir) throws Exception {
        InputStream specStream = getClass().getResourceAsStream("/specs/form1040_2025_spec.json");
        InputStream payloadStream = getClass().getResourceAsStream("/sample_payer_data.json");
        InputStream blankPdfStream = getClass().getResourceAsStream("/f1040_blank.pdf");

        assertNotNull(specStream);
        assertNotNull(payloadStream);
        assertNotNull(blankPdfStream);

        FormSpecification spec = fillerService.loadSpecification(specStream);
        String payerJson = new String(payloadStream.readAllBytes(), StandardCharsets.UTF_8);

        File outputFile = tempDir.resolve("test_form1040_output.pdf").toFile();
        fillerService.generateFilledPdf(blankPdfStream, spec, payerJson, outputFile);

        assertTrue(outputFile.exists(), "Output PDF file should be created");
        assertTrue(outputFile.length() > 0, "Output PDF file should have non-zero size");

        // Verify PDF is readable by PDFBox and contains stamped text
        try (PDDocument doc = Loader.loadPDF(outputFile)) {
            assertEquals(2, doc.getNumberOfPages(), "Form 1040 should have 2 pages");
            org.apache.pdfbox.text.PDFTextStripper stripper = new org.apache.pdfbox.text.PDFTextStripper();
            String extractedText = stripper.getText(doc);
            String cleanText = extractedText.replaceAll("\\s+", "");
            assertTrue(extractedText.contains("ROHIN"), "Should contain taxpayer first name");
            assertTrue(extractedText.contains("SELVA"), "Should contain taxpayer last name");
            assertTrue(extractedText.contains("115001"), "Should contain rounded W2 wages");
            assertTrue(cleanText.contains("123456789"), "Should contain unmasked taxpayer SSN without hyphens");
            assertTrue(cleanText.contains("987654321"), "Should contain dependent SSN without hyphens");
            assertTrue(extractedText.contains("ALEX"), "Should contain dependent 1 first name");
            assertTrue(extractedText.contains("SARAH"), "Should contain dependent 2 first name");
            assertTrue(extractedText.contains("MICHAEL"), "Should contain dependent 3 first name");
            assertTrue(cleanText.contains("987654322"), "Should contain dependent 2 SSN without hyphens");
            assertTrue(cleanText.contains("987654323"), "Should contain dependent 3 SSN without hyphens");
            assertTrue(extractedText.contains("4320.75"), "Should contain exact wages with cents (CURRENCY_EXACT)");
            assertTrue(extractedText.contains("04/15/2026"), "Should contain taxpayer signature date (DATE_MMDDYYYY)");
            assertTrue(extractedText.contains("XXXXX7766"), "Should contain masked former spouse SSN (SSN_MASKED)");
            assertFalse(extractedText.contains("123-45-6789"), "Should not contain hyphenated SSN");
            // Render page 1 and page 2 as images for visual verification
            org.apache.pdfbox.rendering.PDFRenderer renderer = new org.apache.pdfbox.rendering.PDFRenderer(doc);
            java.awt.image.BufferedImage bimPage1 = renderer.renderImageWithDPI(0, 150);
            File preview1 = new File(
                    "C:/Users/rohin/.gemini/antigravity-ide/brain/99f0db0d-cb2e-4cd0-abe1-7f9c23f05038/form1040_filled_page1_preview.png");
            javax.imageio.ImageIO.write(bimPage1, "PNG", preview1);

            java.awt.image.BufferedImage bimPage2 = renderer.renderImageWithDPI(1, 150);
            File preview2 = new File(
                    "C:/Users/rohin/.gemini/antigravity-ide/brain/99f0db0d-cb2e-4cd0-abe1-7f9c23f05038/form1040_filled_page2_preview.png");
            javax.imageio.ImageIO.write(bimPage2, "PNG", preview2);

            // High-resolution crop of all 3 Dependents (x: 36 to 480, y: 430 to 485)
            float scale1 = 150f / 72f;
            int depX = (int) (36 * scale1);
            int depY = (int) ((792 - 485) * scale1);
            int depW = (int) ((475 - 36) * scale1);
            int depH = (int) ((485 - 425) * scale1);
            java.awt.image.BufferedImage depCrop = bimPage1.getSubimage(depX, depY, depW, depH);
            javax.imageio.ImageIO.write(depCrop, "PNG", new File("C:/Users/rohin/.gemini/antigravity-ide/brain/99f0db0d-cb2e-4cd0-abe1-7f9c23f05038/all_dependents_crop.png"));

            // High-resolution crop of Sign Here section with Date (x: 50 to 400, y: 115 to 150)
            int signX = (int) (50 * scale1);
            int signY = (int) ((792 - 150) * scale1);
            int signW = (int) (330 * scale1);
            int signH = (int) (35 * scale1);
            java.awt.image.BufferedImage signCrop = bimPage2.getSubimage(signX, signY, signW, signH);
            javax.imageio.ImageIO.write(signCrop, "PNG", new File("C:/Users/rohin/.gemini/antigravity-ide/brain/99f0db0d-cb2e-4cd0-abe1-7f9c23f05038/sign_here_date_crop.png"));

            // High-resolution crop of Line 1a & Line 1b showing CURRENCY_EXACT (x: 400 to 585, y: 310 to 345)
            int currX = (int) (400 * scale1);
            int currY = (int) ((792 - 345) * scale1);
            int currW = (int) (185 * scale1);
            int currH = (int) (35 * scale1);
            java.awt.image.BufferedImage currCrop = bimPage1.getSubimage(currX, currY, currW, currH);
            javax.imageio.ImageIO.write(currCrop, "PNG", new File("C:/Users/rohin/.gemini/antigravity-ide/brain/99f0db0d-cb2e-4cd0-abe1-7f9c23f05038/exact_currency_crop.png"));

            // High-resolution crop of Line 26 showing SSN_MASKED (x: 100 to 490, y: 435 to 460)
            int maskX = (int) (100 * scale1);
            int maskY = (int) ((792 - 460) * scale1);
            int maskW = (int) (390 * scale1);
            int maskH = (int) (25 * scale1);
            java.awt.image.BufferedImage maskCrop = bimPage2.getSubimage(maskX, maskY, maskW, maskH);
            javax.imageio.ImageIO.write(maskCrop, "PNG", new File("C:/Users/rohin/.gemini/antigravity-ide/brain/99f0db0d-cb2e-4cd0-abe1-7f9c23f05038/masked_ssn_crop.png"));
        }
    }
}

