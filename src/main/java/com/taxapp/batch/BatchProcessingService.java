package com.taxapp.batch;

import com.taxapp.model.FormSpecification;
import com.taxapp.service.Form1040FillerService;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * High-throughput, multi-threaded batch processor that fills tax returns concurrently.
 */
public class BatchProcessingService implements AutoCloseable {

    private final Form1040FillerService fillerService;
    private final int concurrencyLevel;
    private final ExecutorService executorService;

    public BatchProcessingService() {
        this(new Form1040FillerService(), Math.max(2, Runtime.getRuntime().availableProcessors()));
    }

    public BatchProcessingService(int concurrencyLevel) {
        this(new Form1040FillerService(), concurrencyLevel);
    }

    public BatchProcessingService(Form1040FillerService fillerService, int concurrencyLevel) {
        this.fillerService = fillerService != null ? fillerService : new Form1040FillerService();
        this.concurrencyLevel = Math.max(1, concurrencyLevel);
        this.executorService = Executors.newFixedThreadPool(this.concurrencyLevel);
    }

    public int getConcurrencyLevel() {
        return concurrencyLevel;
    }

    /**
     * Scans an input directory for JSON payloads and generates corresponding Form 1040 PDFs in parallel.
     *
     * @param inputDir Directory containing taxpayer *.json files
     * @param outputDir Destination directory for generated *.pdf files
     * @param spec Specification defining field coordinates
     * @param blankPdfStream Official blank IRS template stream
     * @return BatchResult containing aggregate completion metrics
     * @throws IOException If directory traversal or template loading fails
     */
    public BatchResult processDirectory(
            File inputDir,
            File outputDir,
            FormSpecification spec,
            InputStream blankPdfStream) throws IOException {

        if (inputDir == null || !inputDir.isDirectory()) {
            throw new IllegalArgumentException("Invalid input directory: " + (inputDir != null ? inputDir.getPath() : "null"));
        }

        if (outputDir == null) {
            throw new IllegalArgumentException("Output directory cannot be null");
        }

        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }

        File[] jsonFiles = inputDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".json"));
        if (jsonFiles == null || jsonFiles.length == 0) {
            return new BatchResult(0, 0, 0, 0, Collections.emptyMap());
        }

        byte[] blankPdfTemplateBytes = blankPdfStream.readAllBytes();
        long startTime = System.currentTimeMillis();

        AtomicInteger successCounter = new AtomicInteger(0);
        AtomicInteger failureCounter = new AtomicInteger(0);
        Map<String, String> failureErrors = new ConcurrentHashMap<>();

        List<Future<?>> futures = new ArrayList<>();

        for (File jsonFile : jsonFiles) {
            futures.add(executorService.submit(() -> {
                String fileName = jsonFile.getName();
                try {
                    String baseName = fileName.replaceFirst("(?i)\\.json$", "");
                    File destinationPdf = new File(outputDir, baseName + "_Filled_1040.pdf");

                    String payerJson = Files.readString(jsonFile.toPath(), StandardCharsets.UTF_8);
                    try (InputStream pdfInput = new ByteArrayInputStream(blankPdfTemplateBytes)) {
                        fillerService.generateFilledPdf(pdfInput, spec, payerJson, destinationPdf);
                    }

                    successCounter.incrementAndGet();
                } catch (Exception e) {
                    failureCounter.incrementAndGet();
                    failureErrors.put(fileName, e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
                }
            }));
        }

        for (Future<?> future : futures) {
            try {
                future.get();
            } catch (Exception e) {
                // Future execution exceptions are captured per file in failureErrors map
            }
        }

        long duration = System.currentTimeMillis() - startTime;
        return new BatchResult(jsonFiles.length, successCounter.get(), failureCounter.get(), duration, failureErrors);
    }

    @Override
    public void close() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
