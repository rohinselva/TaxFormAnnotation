package com.taxapp.cli;

/**
 * Command-line arguments parser and configuration model.
 */
public class CliOptions {

    private String specPath = "src/main/resources/specs/form1040_2025_spec.json";
    private String dataPath = "src/main/resources/sample_payer_data.json";
    private String blankPdfPath = "src/main/resources/f1040_blank.pdf";
    private String outputPath = "Form1040_Filled_2025.pdf";
    private String batchDir = null;
    private int threads = Math.max(2, Runtime.getRuntime().availableProcessors());
    private boolean help = false;

    public static CliOptions parse(String[] args) {
        CliOptions options = new CliOptions();
        if (args == null || args.length == 0) {
            return options;
        }

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            switch (arg) {
                case "-h", "--help" -> options.help = true;
                case "--spec" -> {
                    if (i + 1 < args.length) options.specPath = args[++i];
                }
                case "--data" -> {
                    if (i + 1 < args.length) options.dataPath = args[++i];
                }
                case "--blank" -> {
                    if (i + 1 < args.length) options.blankPdfPath = args[++i];
                }
                case "-o", "--output" -> {
                    if (i + 1 < args.length) options.outputPath = args[++i];
                }
                case "-b", "--batch" -> {
                    if (i + 1 < args.length) options.batchDir = args[++i];
                }
                case "-t", "--threads" -> {
                    if (i + 1 < args.length) {
                        try {
                            options.threads = Math.max(1, Integer.parseInt(args[++i]));
                        } catch (NumberFormatException ignored) {}
                    }
                }
                default -> {
                    if (arg.startsWith("-")) {
                        System.err.println("Unrecognized option: " + arg);
                    }
                }
            }
        }
        return options;
    }

    public boolean isHelp() { return help; }
    public boolean isBatchMode() { return batchDir != null && !batchDir.trim().isEmpty(); }
    public String getSpecPath() { return specPath; }
    public String getDataPath() { return dataPath; }
    public String getBlankPdfPath() { return blankPdfPath; }
    public String getOutputPath() { return outputPath; }
    public String getBatchDir() { return batchDir; }
    public int getThreads() { return threads; }

    public static void printUsage() {
        System.out.println("IRS Form 1040 AI Overlay Engine - CLI");
        System.out.println("Usage: java -jar tax-form-filler.jar [OPTIONS]");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  --spec <file>       Path to FormSpecification JSON (default: specs/form1040_2025_spec.json)");
        System.out.println("  --data <file>       Path to taxpayer data JSON payload (default: sample_payer_data.json)");
        System.out.println("  --blank <file>      Path to blank IRS 1040 PDF template (default: f1040_blank.pdf)");
        System.out.println("  -o, --output <file> Destination PDF file or output directory (default: Form1040_Filled_2025.pdf)");
        System.out.println("  -b, --batch <dir>   Batch directory containing multiple taxpayer JSON files");
        System.out.println("  -t, --threads <num> Number of concurrent worker threads for batch mode (default: CPU cores)");
        System.out.println("  -h, --help          Show this help message and exit");
    }
}
