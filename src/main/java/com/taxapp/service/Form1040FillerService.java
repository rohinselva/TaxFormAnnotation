package com.taxapp.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import com.jayway.jsonpath.PathNotFoundException;
import com.taxapp.formatter.FormatterRegistry;
import com.taxapp.model.FieldAnnotation;
import com.taxapp.model.FormSpecification;
import com.taxapp.render.FieldRenderer;
import com.taxapp.render.RenderContext;
import com.taxapp.render.RendererFactory;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotation;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationWidget;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Service orchestrating Form 1040 PDF generation by coordinating JSONPath extraction,
 * modular strategy formatting, and canvas coordinate rendering.
 */
public class Form1040FillerService {

    private final ObjectMapper objectMapper;
    private final Configuration jsonPathConfig;
    private final FormatterRegistry formatterRegistry;
    private final RendererFactory rendererFactory;

    public Form1040FillerService() {
        this(new FormatterRegistry(), new RendererFactory());
    }

    public Form1040FillerService(FormatterRegistry formatterRegistry, RendererFactory rendererFactory) {
        this.objectMapper = new ObjectMapper();
        this.jsonPathConfig = Configuration.defaultConfiguration()
                .addOptions(Option.DEFAULT_PATH_LEAF_TO_NULL, Option.SUPPRESS_EXCEPTIONS);
        this.formatterRegistry = formatterRegistry != null ? formatterRegistry : new FormatterRegistry();
        this.rendererFactory = rendererFactory != null ? rendererFactory : new RendererFactory();
    }

    public FormatterRegistry getFormatterRegistry() {
        return formatterRegistry;
    }

    public RendererFactory getRendererFactory() {
        return rendererFactory;
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
     * payer payload, formatting values via registered strategies, and rendering them.
     *
     * @param blankPdfInput Stream pointing to the official blank IRS 1040 PDF
     * @param spec          The static FormSpecification containing field coordinates and rules
     * @param payerJson     The dynamic user tax calculation payload
     * @param outputFile    Destination file for the filled PDF
     * @throws IOException If reading or saving the PDF fails
     */
    public void generateFilledPdf(
            InputStream blankPdfInput,
            FormSpecification spec,
            String payerJson,
            File outputFile) throws IOException {

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

                // Page numbers in the spec are 1-based; PDFBox uses 0-based indexing
                int targetPageIndex = field.page() - 1;
                if (targetPageIndex < 0 || targetPageIndex >= document.getNumberOfPages()) {
                    continue;
                }

                PDPage page = document.getPage(targetPageIndex);
                String cleanValue = sanitizeForWinAnsi(printableValue).trim();

                RenderContext renderContext = new RenderContext(document, page, fontRegular, fontDingbats);
                FieldRenderer renderer = rendererFactory.getRenderer(field.format());
                renderer.render(renderContext, field, cleanValue);
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
     * Queries the JSON context using JSONPath and delegates formatting to the registered FormatterRegistry.
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

        return formatterRegistry.format(field.format(), rawValue);
    }

    /**
     * Sanitizes strings to only contain characters encodable in standard PDF WinAnsi font encoding.
     */
    private String sanitizeForWinAnsi(String input) {
        if (input == null) {
            return "";
        }
        return input.replace('’', '\'')
                .replace('‘', '\'')
                .replace('“', '"')
                .replace('”', '"')
                .replace('–', '-')
                .replace('—', '-');
    }
}
