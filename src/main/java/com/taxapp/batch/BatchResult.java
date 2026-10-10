package com.taxapp.batch;

import java.util.Collections;
import java.util.Map;

/**
 * Summary metrics and diagnostics for a batch PDF generation job.
 *
 * @param totalFiles Total number of input payload files identified
 * @param successCount Number of PDFs generated successfully
 * @param failureCount Number of failures encountered
 * @param durationMs Total execution elapsed time in milliseconds
 * @param errors Map of failing file names to error messages
 */
public record BatchResult(
    int totalFiles,
    int successCount,
    int failureCount,
    long durationMs,
    Map<String, String> errors
) {
    public BatchResult {
        if (errors == null) {
            errors = Map.of();
        } else {
            errors = Collections.unmodifiableMap(errors);
        }
    }

    public boolean isAllSuccessful() {
        return failureCount == 0 && totalFiles > 0;
    }

    @Override
    public String toString() {
        return String.format("BatchResult[total=%d, succeeded=%d, failed=%d, duration=%dms]",
                totalFiles, successCount, failureCount, durationMs);
    }
}
