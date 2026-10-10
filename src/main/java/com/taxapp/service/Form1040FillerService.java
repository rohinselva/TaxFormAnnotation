package com.taxapp.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import com.jayway.jsonpath.PathNotFoundException;
import com.taxapp.model.FieldAnnotation;
import com.taxapp.model.FormatType;
import com.taxapp.model.FormSpecification;
import com.taxapp.model.TextAlignment;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotation;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationWidget;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import com.taxapp.validation.SpecificationValidator;
import com.taxapp.validation.TaxDataValidator;
import com.taxapp.validation.ValidationException;
import com.taxapp.validation.ValidationPolicy;
import com.taxapp.validation.ValidationResult;

/**
 * Service responsible for evaluating JSONPath queries from the
 * FormSpecification against
 * the dynamic payer payload, formatting values according to tax rules,
 * calculating exact
 * baseline/alignment coordinates, and stamping the final text onto the IRS 1040
 * PDF template.
 */
public class Form1040FillerService {

    private final ObjectMapper objectMapper;
    private final Configuration jsonPathConfig;
    private final TaxDataValidator taxDataValidator;
    private final SpecificationValidator specificationValidator;
    private ValidationPolicy validationPolicy = ValidationPolicy.LENIENT;

    public Form1040FillerService() {
        this.objectMapper = new ObjectMapper();
        this.jsonPathConfig = Configuration.defaultConfiguration()
                .addOptions(Option.DEFAULT_PATH_LEAF_TO_NULL, Option.SUPPRESS_EXCEPTIONS);
        this.taxDataValidator = new TaxDataValidator();
        this.specificationValidator = new SpecificationValidator();
    }

    public ValidationPolicy getValidationPolicy() {
        return validationPolicy;
    }

    public void setValidationPolicy(ValidationPolicy validationPolicy) {
        this.validationPolicy = validationPolicy != null ? validationPolicy : ValidationPolicy.LENIENT;
    }

    /**
     * Executes pre-flight validation on both the specification and taxpayer payload.
     *
     * @param spec      Form specification
     * @param payerJson Dynamic taxpayer JSON payload
     * @return ValidationResult aggregating all discovered issues
     */
    public ValidationResult validate(FormSpecification spec, String payerJson) {
        ValidationResult result = new ValidationResult();
        if (spec != null) {
            result.merge(specificationValidator.validate(spec));
        }
        if (payerJson != null) {
            result.merge(taxDataValidator.validate(payerJson));
        }
        return result;
    }

    /**
     * Loads the static FormSpecification configuration JSON from disk or classpath.
     *
     * @param specStream Stream containing the form specification JSON
     * @return Deserialized FormSpecification object
     * @throws IOException If parsing fails
     */
    public FormSpecification loadSpecification(InputStream specStream) throws IOException {
        return objectMapper.readValue(specStream, FormSpecification.class);
    }

    /**
     * Fills a blank 1040 PDF template by evaluating JSONPath queries against the
     * payer payload.
     *
     * @param blankPdfInput Stream pointing to the official blank IRS 1040 PDF
     * @param spec          The static FormSpecification containing field
     *                      coordinates and rules
     * @param payerJson     The dynamic user tax calculation payload
     * @param outputFile    Destination file for the filled PDF
     * @throws IOException If reading or saving the PDF fails
     */
    public void generateFilledPdf(
            InputStream blankPdfInput,
            FormSpecification spec,
            String payerJson,
            File outputFile) throws IOException {

        // Pre-flight validation according to configured policy
        if (validationPolicy != ValidationPolicy.SKIP) {
            ValidationResult validationResult = validate(spec, payerJson);
            if (validationPolicy == ValidationPolicy.STRICT && validationResult.hasErrors()) {
                throw new ValidationException("PDF generation aborted due to pre-flight validation errors", validationResult);
            }
        }

        DocumentContext documentContext = JsonPath.using(jsonPathConfig).parse(payerJson);

        byte[] pdfBytes = blankPdfInput.readAllBytes();
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {

            PDType1Font fontRegular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            PDType1Font fontDingbats = new PDType1Font(Standard14Fonts.FontName.ZAPF_DINGBATS);

            for (FieldAnnotation field : spec.fields()) {
                String printableValue = extractAndFormat(documentContext, field);
                if (printableValue == null || printableValue.trim().isEmpty()) {
                    continue;
                }

                printableValue = sanitizeForWinAnsi(printableValue);

                // 1. Clean the string to remove any invisible leading/trailing spaces
                String cleanValue = printableValue.trim();

                // Page numbers in the spec are 1-based; PDFBox uses 0-based indexing
                int targetPageIndex = field.page() - 1;
                if (targetPageIndex < 0 || targetPageIndex >= document.getNumberOfPages()) {
                    continue;
                }

                PDPage page = document.getPage(targetPageIndex);

                // 2. Setup font metrics
                float fontSize;
                PDType1Font selectedFont;
                if (field.format() == FormatType.CHECKBOX_BOOLEAN) {
                    selectedFont = fontDingbats;
                    fontSize = 8.5f;
                    if ("4".equals(cleanValue) || "X".equalsIgnoreCase(cleanValue)) {
                        cleanValue = "\u2713";
                    }
                } else {
                    selectedFont = fontRegular;
                    fontSize = 8.5f;
                }

                // Dynamic auto-shrinking: prevent text overflowing box width
                if (field.format() != FormatType.CHECKBOX_BOOLEAN && field.position().width() > 10.0) {
                    float maxAllowedWidth = (float) (field.position().width() - 4.0);
                    while (fontSize > 5.5f
                            && (selectedFont.getStringWidth(cleanValue) / 1000.0f) * fontSize > maxAllowedWidth) {
                        fontSize -= 0.5f;
                    }
                }

                // 3. Calculate the physical width of the string in PDF points
                float textWidth = (selectedFont.getStringWidth(cleanValue) / 1000.0f) * fontSize;

                // 4. Calculate dynamic coordinates
                float startX = (float) field.position().x();
                float startY = (float) field.position().y();

                // Offset Y slightly to account for the font baseline (vertical centering)
                if (field.format() == FormatType.CHECKBOX_BOOLEAN) {
                    startY += 1.0f; // Centered vertical baseline for checkbox tick
                } else {
                    startY += 2.5f;
                }

                // --- NEW LOGIC FOR COMB STRUCTURES ---
                if (field.format() == FormatType.SSN_COMBED) {
                    String cleanSSN = cleanValue.replaceAll("[^0-9]", "");

                    if (cleanSSN.length() == 9) {
                        String part1 = cleanSSN.substring(0, 3);
                        String part2 = cleanSSN.substring(3, 5);
                        String part3 = cleanSSN.substring(5, 9);

                        float boxWidth = (float) field.position().width();

                        // The IRS SSN box has dashed lines at roughly 33% and 55%.
                        // We calculate exact X starting points for each of the 3 segments.
                        float xOffset1 = startX + 3.0f;
                        float xOffset2 = startX + (boxWidth * 0.36f);
                        float xOffset3 = startX + (boxWidth * 0.60f);

                        try (PDPageContentStream stream = new PDPageContentStream(
                                document,
                                page,
                                PDPageContentStream.AppendMode.APPEND,
                                true,
                                true)) {
                            // Draw Part 1 (XXX)
                            stream.beginText();
                            stream.setFont(selectedFont, fontSize);
                            stream.newLineAtOffset(xOffset1, startY);
                            stream.showText(part1);
                            stream.endText();

                            // Draw Part 2 (XX)
                            stream.beginText();
                            stream.setFont(selectedFont, fontSize);
                            stream.newLineAtOffset(xOffset2, startY);
                            stream.showText(part2);
                            stream.endText();

                            // Draw Part 3 (XXXX)
                            stream.beginText();
                            stream.setFont(selectedFont, fontSize);
                            stream.newLineAtOffset(xOffset3, startY);
                            stream.showText(part3);
                            stream.endText();
                        }
                    }

                    // Skip the default drawing below for comb fields
                    continue;
                }
                // --- END COMB LOGIC ---

                // Adjust X based on alignment rules
                else if (field.format() == FormatType.CURRENCY_ROUNDED || field.format() == FormatType.CURRENCY_EXACT
                        || field.alignment() == TextAlignment.RIGHT) {
                    // RIGHT ALIGN: Push to the right edge of the bounding box, minus a 2-point
                    // padding
                    float rightEdge = startX + (float) field.position().width();
                    startX = rightEdge - textWidth - 2.0f;
                } else if (field.format() == FormatType.CHECKBOX_BOOLEAN || field.alignment() == TextAlignment.CENTER) {
                    // CENTER ALIGN: Find the middle of the small checkbox
                    startX = startX + ((float) field.position().width() - textWidth) / 2.0f;
                } else {
                    // LEFT ALIGN: Add a standard 2-point padding from the left wall
                    startX += 2.0f;
                }

                // 5. Draw the text with the corrected math
                try (PDPageContentStream stream = new PDPageContentStream(
                        document,
                        page,
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

            // Strip any leftover template interactive form widgets so the output PDF is 100% flat
            document.getDocumentCatalog().getCOSObject().removeItem(COSName.ACRO_FORM);
            for (PDPage p : document.getPages()) {
                List<PDAnnotation> annots = new ArrayList<>();
                for (PDAnnotation annot : p.getAnnotations()) {
                    if (!(annot instanceof PDAnnotationWidget)) {
                        annots.add(annot);
                    }
                }
                p.setAnnotations(annots);
            }

            // Ensure target directory exists before saving
            if (outputFile.getParentFile() != null && !outputFile.getParentFile().exists()) {
                outputFile.getParentFile().mkdirs();
            }

            document.save(outputFile);
        }
    }

    /**
     * Queries the JSON context using JSONPath and formats the result according to
     * FormatType.
     */
    public String extractAndFormat(DocumentContext ctx, FieldAnnotation field) {
        Object rawValue;
        try {
            rawValue = ctx.read(field.dataPath());
        } catch (PathNotFoundException e) {
            return "";
        }

        if (rawValue == null) {
            return "";
        }

        return switch (field.format()) {
            case UPPERCASE_TEXT -> rawValue.toString().toUpperCase().trim();

            case CURRENCY_ROUNDED -> {
                if (rawValue instanceof Number num) {
                    yield String.valueOf(Math.round(num.doubleValue()));
                }
                String cleaned = rawValue.toString().replaceAll("[$,]", "").trim();
                yield cleaned.split("\\.")[0];
            }

            case CURRENCY_EXACT -> {
                if (rawValue instanceof Number num) {
                    yield String.format("%.2f", num.doubleValue());
                }
                yield rawValue.toString().replaceAll("[$,]", "").trim();
            }

            case CHECKBOX_BOOLEAN -> {
                if (rawValue instanceof Boolean bool && Boolean.TRUE.equals(bool)) {
                    yield "4"; // In ZapfDingbats, '4' is a checkmark (tick symbol)
                }
                if (rawValue instanceof String str && ("true".equalsIgnoreCase(str) || "yes".equalsIgnoreCase(str))) {
                    yield "4";
                }
                yield "";
            }

            case SSN_COMBED, SSN_UNMASKED -> {
                // Strip hyphens, spaces, and any non-digit characters so SSN is clean digits
                // without hyphens
                String cleanDigits = rawValue.toString().replaceAll("[^0-9]", "").trim();
                yield cleanDigits;
            }

            case SSN_MASKED -> {
                String cleanDigits = rawValue.toString().replaceAll("[^0-9]", "").trim();
                if (cleanDigits.length() == 9) {
                    yield "XXXXX" + cleanDigits.substring(5);
                }
                yield cleanDigits;
            }

            case DATE_MMDDYYYY -> {
                String str = rawValue.toString().trim();
                if (str.matches("\\d{4}-\\d{2}-\\d{2}")) {
                    String[] p = str.split("-");
                    yield p[1] + "/" + p[2] + "/" + p[0];
                }
                yield str.replaceAll("[^0-9/]", "").trim();
            }
        };
    }

    /**
     * Sanitizes strings to only contain characters encodable in standard PDF
     * WinAnsi font encoding.
     */
    private String sanitizeForWinAnsi(String input) {
        if (input == null)
            return "";
        return input.replace('’', '\'')
                .replace('‘', '\'')
                .replace('“', '"')
                .replace('”', '"')
                .replace('–', '-')
                .replace('—', '-');
    }
}
