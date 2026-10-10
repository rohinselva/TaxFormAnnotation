package com.taxapp.batch;

import com.taxapp.model.FormSpecification;
import com.taxapp.service.Form1040FillerService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class BatchProcessingServiceTest {

    @Test
    @DisplayName("Should successfully process multiple JSON payloads concurrently")
    void testConcurrentBatchProcessing(@TempDir Path tempDir) throws Exception {
        Path inputDir = tempDir.resolve("input_payers");
        Path outputDir = tempDir.resolve("output_pdfs");
        Files.createDirectories(inputDir);
        Files.createDirectories(outputDir);

        // Create 4 sample taxpayer payloads
        String validPayloadTemplate = """
                {
                    "taxpayer": {
                        "firstNameAndInitial": "User%d",
                        "lastName": "Taxpayer",
                        "ssn": "12345678%d",
                        "signatureDate": "2026-04-15"
                    },
                    "income": {
                        "w2Wages": %d000.00
                    }
                }
                """;

        for (int i = 1; i <= 4; i++) {
            Files.writeString(inputDir.resolve("taxpayer_" + i + ".json"), String.format(validPayloadTemplate, i, i, 50 + i));
        }

        // Also add 1 corrupted JSON file to verify error resilience
        Files.writeString(inputDir.resolve("taxpayer_corrupt.json"), "{ invalid json ");

        Form1040FillerService fillerService = new Form1040FillerService();
        try (InputStream specStream = getClass().getResourceAsStream("/specs/form1040_2025_spec.json");
             InputStream blankPdfStream = getClass().getResourceAsStream("/f1040_blank.pdf");
             BatchProcessingService batchService = new BatchProcessingService(fillerService, 4)) {

            assertNotNull(specStream);
            assertNotNull(blankPdfStream);
            FormSpecification spec = fillerService.loadSpecification(specStream);

            BatchResult result = batchService.processDirectory(
                    inputDir.toFile(),
                    outputDir.toFile(),
                    spec,
                    blankPdfStream
            );

            assertEquals(5, result.totalFiles());
            assertEquals(4, result.successCount(), "4 valid files should succeed");
            assertEquals(1, result.failureCount(), "1 corrupt file should fail without terminating entire batch");
            assertTrue(result.errors().containsKey("taxpayer_corrupt.json"));
            assertTrue(result.durationMs() >= 0);

            // Verify generated PDF files exist
            for (int i = 1; i <= 4; i++) {
                File generatedPdf = outputDir.resolve("taxpayer_" + i + "_Filled_1040.pdf").toFile();
                assertTrue(generatedPdf.exists(), "Output PDF should exist for taxpayer " + i);
                assertTrue(generatedPdf.length() > 0);
            }
        }
    }
}
