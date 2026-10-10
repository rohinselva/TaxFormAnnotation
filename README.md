# TaxFormAnnotation: IRS Form 1040 AI Overlay Engine

A high-performance, coordinate-based Java engine for populating official IRS Form 1040 PDF tax returns from dynamic JSON payloads.

---

## Overview

Unlike legacy approaches that depend on fragile and vendor-specific AcroForm / XFA form widgets, this engine operates on a **direct canvas coordinate overlay** model. By rendering vector text and glyphs directly onto the PDF content stream at exact physical points, the engine works seamlessly on any blank IRS PDF, completely immune to interactive widget quirks, reader incompatibilities, or form-locking issues.

The generated PDF is **100% flattened**, print-ready, and compliant with IRS visual filing standards.

---

## Key Features

* **Static Coordinate Specification (`form1040_2025_spec.json`)**
  * Decouples coordinate mapping from application code.
  * Defines exact physical coordinates (`x`, `y`, `width`, `height`), text alignment (`LEFT`, `CENTER`, `RIGHT`), and format types for 70+ Form 1040 fields.
* **Dynamic JSONPath Binding**
  * Binds taxpayer data to coordinates dynamically using standard JSONPath queries (e.g., `$.taxpayer.firstNameAndInitial`, `$.dependents[1].ssn`, `$.income.w2Wages`).
* **Combed Structure Alignment (`SSN_COMBED`)**
  * Solves the IRS dashed-line box layout by splitting 9-digit SSNs into physical sub-segments (`XXX`, `XX`, `XXXX`) and placing each segment precisely inside its designated compartment.
* **Rich Tax Formatting (`FormatType`)**
  * `UPPERCASE_TEXT`: Auto-sanitized, uppercase text for names, addresses, and occupations.
  * `CURRENCY_ROUNDED`: Rounds currency values to the nearest integer per IRS tax table rules.
  * `CURRENCY_EXACT`: Preserves dollars and cents (e.g. `4320.75`) for exact wage and credit fields.
  * `CHECKBOX_BOOLEAN`: Renders authentic vector checkmarks (`✓`) using standard `ZapfDingbats` glyphs.
  * `DATE_MMDDYYYY`: Automatically standardizes ISO (`YYYY-MM-DD`) and formatted strings into `MM/DD/YYYY`.
  * `SSN_MASKED`: Masks sensitive identification numbers (`XXXXX7766`) for client privacy copies.
  * `SSN_COMBED` & `SSN_UNMASKED`: Automatically cleans hyphens, spaces, and non-numeric characters.
* **Complete Widget Flattening**
  * Strips leftover AcroForm widget annotations so users opening the PDF in Adobe Acrobat or browser viewers never experience field highlight glitches or accidental cross (`X`) toggles.
* **Live Developer Workflow**
  * Automatically detects and reloads live updates in `src/main/resources/sample_payer_data.json` during local execution without classpath cache delays.

---

## Architecture & Data Flow

```
+---------------------------+       +------------------------------------+
|  sample_payer_data.json   |       |    form1040_2025_spec.json         |
|  (Dynamic Taxpayer Data)  |       |  (Physical Coordinate Definitions) |
+-------------+-------------+       +-----------------+------------------+
              |                                       |
              +-------------------+-------------------+
                                  |
                                  v
                    +---------------------------+
                    |   Form1040FillerService   |
                    |     (Java 17 / PDFBox)    |
                    +-------------+-------------+
                                  |
               Loads              v               Stamps & Flattens
     +-------------------> [f1040_blank.pdf] ------------------> [Form1040_Filled_2025.pdf]
```

---

## Tech Stack

* **Language:** Java 17 (LTS)
* **PDF Engine:** Apache PDFBox 3.0.2
* **JSON Processing:** Jackson Databind 2.17.0
* **JSON Querying:** Jayway JsonPath 2.9.0
* **Logging:** SLF4J (with `slf4j-nop` for clean, silent execution)
* **Testing:** JUnit 5 (Jupiter 5.10.2)
* **Build System:** Apache Maven 3.8+

---

## Project Structure

```
TaxFormAnnotation/
├── pom.xml                                   # Maven project dependencies and build configuration
├── README.md                                 # Project documentation
├── SPECIFICATION.md                          # Formal Tax Form Annotation Specification (TFAS-1.0)
├── ARCHITECTURE.md                           # Architecture and deep-dive technical flow guide
├── .gitignore                                # Ignores build artifacts and generated PDFs
└── src/
    ├── main/
    │   ├── java/com/taxapp/
    │   │   ├── Main.java                     # CLI entry point for PDF generation
    │   │   ├── model/
    │   │   │   ├── FieldAnnotation.java      # Model representing a mapped form field
    │   │   │   ├── FormatType.java           # Enum of all supported data formats
    │   │   │   ├── FormSpecification.java    # Model for the complete form specification
    │   │   │   ├── PageDimensions.java       # PDF page width/height dimensions
    │   │   │   ├── Position.java             # Bounding box coordinates (x, y, w, h)
    │   │   │   └── TextAlignment.java        # Alignment rules (LEFT, CENTER, RIGHT)
    │   │   └── service/
    │   │       └── Form1040FillerService.java# Core vector overlay, formatting & stamping service
    │   └── resources/
    │       ├── f1040_blank.pdf               # Official IRS Form 1040 blank template
    │       ├── sample_payer_data.json        # Sample input tax data
    │       └── specs/
    │           └── form1040_2025_spec.json   # 2025 Form 1040 coordinate specifications
    └── test/
        └── java/com/taxapp/service/
            ├── CoordinateCalibratorTest.java # Coordinate calibration & image crop tests
            └── Form1040FillerServiceTest.java# Comprehensive end-to-end unit tests
```

---

## Getting Started

### Prerequisites
* **Java Development Kit (JDK):** Version 17 or higher
* **Apache Maven:** Version 3.8 or higher
* **Git**

### Build the Project
```bash
mvn clean compile
```

### Run Automated Tests
```bash
mvn test
```

### Generate a Filled Form 1040 PDF
Run the `Main` class to generate `Form1040_Filled_2025.pdf`:

```bash
mvn exec:java "-Dexec.mainClass=com.taxapp.Main"
```

The output file will be saved in your project root:
```
Form1040_Filled_2025.pdf
```

---

## Customizing Taxpayer Data

To fill the form with your own data, edit `src/main/resources/sample_payer_data.json`:

```json
{
  "taxpayer": {
    "firstNameAndInitial": "ROHIN",
    "lastName": "SELVA",
    "ssn": "123-45-6789",
    "address": {
      "street": "100 CONGRESS AVE",
      "city": "AUSTIN",
      "state": "TX",
      "zipCode": "78701"
    },
    "occupation": "SOFTWARE ARCHITECT",
    "signatureDate": "04/15/2026"
  },
  "filingStatus": {
    "isSingle": true
  },
  "dependents": [
    {
      "firstName": "ALEX",
      "lastName": "DOE",
      "ssn": "987-65-4321",
      "relationship": "SON"
    }
  ],
  "income": {
    "w2Wages": 115000.50
  }
}
```
Run `Main` and the new data will immediately be reflected in the generated return!

---

## Author
* **Rohin Selva** - [rohinselva](https://github.com/rohinselva)
