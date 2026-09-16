package com.taxapp;

import com.taxapp.model.FormSpecification;
import com.taxapp.service.Form1040FillerService;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class Main {

    public static void main(String[] args) {
        System.out.println("==========================================================");
        System.out.println("   IRS Form 1040 AI Autofill & PDF Overlay Engine         ");
        System.out.println("==========================================================");

        try {
            Form1040FillerService fillerService = new Form1040FillerService();

            // 1. Load the static Form 1040 specification JSON
            System.out.println("[1/4] Loading Form Specification: /specs/form1040_2025_spec.json");
            InputStream specStream = loadResource("src/main/resources/specs/form1040_2025_spec.json", "/specs/form1040_2025_spec.json");
            FormSpecification spec = fillerService.loadSpecification(specStream);
            System.out.println("      Loaded form: " + spec.formId() + " (Tax Year: " + spec.taxYear() + 
                               ", Fields: " + spec.fields().size() + ")");

            // 2. Load the dynamic user tax payload JSON
            System.out.println("[2/4] Loading Dynamic Taxpayer Payload: /sample_payer_data.json");
            InputStream userPayloadStream = loadResource("src/main/resources/sample_payer_data.json", "/sample_payer_data.json");
            String payerJson = new String(userPayloadStream.readAllBytes(), StandardCharsets.UTF_8);

            // 3. Load the official blank IRS 1040 PDF template
            System.out.println("[3/4] Loading Official Blank IRS Form 1040 PDF: /f1040_blank.pdf");
            InputStream blankPdfStream = loadResource("src/main/resources/f1040_blank.pdf", "/f1040_blank.pdf");

            // 4. Generate the filled PDF with aligned overlays
            File destinationFile = new File("Form1040_Filled_2025.pdf");
            System.out.println("[4/4] Stamping dynamic values onto Form 1040 template...");
            fillerService.generateFilledPdf(blankPdfStream, spec, payerJson, destinationFile);

            System.out.println("\nSUCCESS: Form 1040 generated successfully!");
            System.out.println("Output File: " + destinationFile.getAbsolutePath());
            System.out.println("File Size:   " + destinationFile.length() + " bytes");
            System.out.println("==========================================================");

        } catch (Exception e) {
            System.err.println("\nERROR: Failed to generate Form 1040 PDF:");
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static InputStream loadResource(String diskPath, String classpath) throws java.io.IOException {
        File file = new File(diskPath);
        if (file.exists()) {
            return new java.io.FileInputStream(file);
        }
        InputStream stream = Main.class.getResourceAsStream(classpath);
        if (stream == null) {
            throw new IllegalStateException("Could not find resource on disk (" + diskPath + ") or classpath (" + classpath + ")");
        }
        return stream;
    }
}
