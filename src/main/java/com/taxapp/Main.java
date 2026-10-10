package com.taxapp;

import com.taxapp.cli.CliRunner;

/**
 * Main application entry point for command-line execution.
 */
public class Main {

    public static void main(String[] args) {
        CliRunner runner = new CliRunner();
        int exitCode = runner.execute(args);
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }
}
