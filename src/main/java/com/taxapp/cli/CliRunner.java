package com.taxapp.cli;

import com.taxapp.batch.BatchProcessingService;
import com.taxapp.batch.BatchResult;
import com.taxapp.model.FormSpecification;
import com.taxapp.service.Form1040FillerService;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * CLI execution coordinator with user banners, batch processing dispatch, and standard exit codes.
 */
public class CliRunner {

    /**
     * Executes the CLI workflow according to supplied arguments.
     *
     * @param args Command-line arguments
     * @return Exit code (0 = success, 1 = usage/syntax, 2 = execution error)
     */
    public int execute(String[] args) {
        CliOptions options = CliOptions.parse(args);

        if (options.isHelp()) {
            CliOptions.printUsage();
            return 0;
        }

        System.out.println("==========================================================");
        System.out.println("   IRS Form 1040 AI Autofill & PDF Overlay Engine         ");
        System.out.println("==========================================================");

        try {
            Form1040FillerService fillerService = new Form1040FillerService();

            // 1. Load Form Specification
            System.out.println("[1/3] Loading Form Specification: " + options.getSpecPath());
            InputStream specStream = resolveResource(options.getSpecPath(), "/specs/form1040_2025_spec.json");
            FormSpecification spec = fillerService.loadSpecification(specStream);
            System.out.println("      Loaded form: " + spec.formId() + " (Tax Year: " + spec.taxYear()
                    + ", Fields: " + spec.fields().size() + ")");

            // 2. Load Blank PDF Template
            System.out.println("[2/3] Loading Blank IRS 1040 Template: " + options.getBlankPdfPath());
            InputStream blankPdfStream = resolveResource(options.getBlankPdfPath(), "/f1040_blank.pdf");

            // 3. Batch Mode vs Single File Mode
            if (options.isBatchMode()) {
                File inputDir = new File(options.getBatchDir());
                File outputDir = new File(options.getOutputPath());
                System.out.println("[3/3] Running Batch Mode on directory: " + inputDir.getAbsolutePath());
                System.out.println("      Concurrency level: " + options.getThreads() + " worker threads");

                try (BatchProcessingService batchService = new BatchProcessingService(fillerService, options.getThreads())) {
                    BatchResult result = batchService.processDirectory(inputDir, outputDir, spec, blankPdfStream);
                    System.out.println("\nBATCH COMPLETE: " + result);
                    if (!result.errors().isEmpty()) {
                        System.err.println("Batch Failures (" + result.failureCount() + "):");
                        result.errors().forEach((file, err) -> System.err.println(" - " + file + ": " + err));
                        return 2;
                    }
                }
            } else {
                System.out.println("[3/3] Loading Taxpayer Payload: " + options.getDataPath());
                InputStream dataStream = resolveResource(options.getDataPath(), "/sample_payer_data.json");
                String payerJson = new String(dataStream.readAllBytes(), StandardCharsets.UTF_8);

                File outputFile = new File(options.getOutputPath());
                System.out.println("      Generating PDF -> " + outputFile.getAbsolutePath());
                fillerService.generateFilledPdf(blankPdfStream, spec, payerJson, outputFile);

                System.out.println("\nSUCCESS: Form 1040 generated successfully!");
                System.out.println("Output File: " + outputFile.getAbsolutePath());
                System.out.println("File Size:   " + outputFile.length() + " bytes");
            }

            System.out.println("==========================================================");
            return 0;

        } catch (Exception e) {
            System.err.println("\nFATAL ERROR during execution: " + e.getMessage());
            e.printStackTrace();
            return 2;
        }
    }

    private InputStream resolveResource(String diskPath, String fallbackClasspath) throws IOException {
        File file = new File(diskPath);
        if (file.exists()) {
            return new FileInputStream(file);
        }
        InputStream stream = CliRunner.class.getResourceAsStream(fallbackClasspath);
        if (stream == null) {
            throw new IOException("Unable to locate resource on disk (" + diskPath + ") or classpath (" + fallbackClasspath + ")");
        }
        return stream;
    }
}
