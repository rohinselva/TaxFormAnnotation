package com.taxapp.cli;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CliOptionsTest {

    @Test
    @DisplayName("Should use default paths when no CLI arguments provided")
    void testDefaultOptions() {
        CliOptions options = CliOptions.parse(new String[]{});

        assertFalse(options.isHelp());
        assertFalse(options.isBatchMode());
        assertTrue(options.getSpecPath().contains("form1040_2025_spec.json"));
        assertTrue(options.getDataPath().contains("sample_payer_data.json"));
        assertTrue(options.getBlankPdfPath().contains("f1040_blank.pdf"));
        assertEquals("Form1040_Filled_2025.pdf", options.getOutputPath());
        assertTrue(options.getThreads() >= 1);
    }

    @Test
    @DisplayName("Should correctly parse customized CLI arguments")
    void testCustomOptions() {
        String[] args = {
                "--spec", "custom_spec.json",
                "--data", "taxpayer_jane.json",
                "--blank", "custom_blank.pdf",
                "-o", "output.pdf",
                "-t", "8"
        };

        CliOptions options = CliOptions.parse(args);

        assertEquals("custom_spec.json", options.getSpecPath());
        assertEquals("taxpayer_jane.json", options.getDataPath());
        assertEquals("custom_blank.pdf", options.getBlankPdfPath());
        assertEquals("output.pdf", options.getOutputPath());
        assertEquals(8, options.getThreads());
        assertFalse(options.isBatchMode());
    }

    @Test
    @DisplayName("Should detect batch mode and help flags")
    void testBatchAndHelpFlags() {
        String[] batchArgs = { "--batch", "./incoming_payers", "-o", "./filled_returns" };
        CliOptions batchOptions = CliOptions.parse(batchArgs);
        assertTrue(batchOptions.isBatchMode());
        assertEquals("./incoming_payers", batchOptions.getBatchDir());
        assertEquals("./filled_returns", batchOptions.getOutputPath());

        String[] helpArgs = { "--help" };
        CliOptions helpOptions = CliOptions.parse(helpArgs);
        assertTrue(helpOptions.isHelp());
    }
}
