# Design Document: Tax Form Annotation Specification

**Author:** Rohin Selva

**Status:** Active / Production Standard

**Target:** IRS Form 1040 & General Tax Schedules

## 1. Overview and Background

Automating IRS tax returns is difficult. High-speed IRS scanners require absolute precision—text must fit perfectly inside physical bounding boxes.

Historically, systems rely on the PDF's built-in interactive fields (AcroForms). This is a fragile approach. The IRS frequently renames internal fields, and different PDF viewers render checkboxes inconsistently. If we trust the PDF metadata, our system breaks when the IRS updates their forms.

**The Solution:** We are moving to a direct 2D canvas overlay model. We will ignore the PDF's interactive fields. Instead, we use an external JSON file to map exact X/Y coordinates on the page. Our engine reads a taxpayer's data, formats it, and stamps it directly onto the blank PDF canvas. Finally, we delete all interactive widgets to create a flat, secure document.

## 2. Goals & Non-Goals

**Goals:**

* Decouple form layout from business logic.
* Ensure pixel-perfect placement for IRS optical scanners.
* Handle complex partitioned boxes (like SSNs).
* Produce a flat, tamper-proof PDF.

**Non-Goals:**

* Calculating tax math (this engine only handles rendering).
* Storing taxpayer data (data is passed in at runtime).

## 3. System Architecture

The system is divided into three isolated layers. It runs as a stateless service, allowing safe concurrent processing for multiple users.

1. **Data Layer:** A dynamic JSON object containing the user's calculated tax data.
2. **Specification Layer:** A static JSON file (`form1040_2025_spec.json`) mapping the exact coordinates, fonts, and data paths for the blank form.
3. **Overlay Engine:** The Java service that merges the Data and the Specification. It stamps the text onto the PDF and flattens the file.

## 4. Implementation Details

### 4.1 Execution Flow

1. **Load Assets:** Fetch the static JSON spec, the user's data payload, and the blank IRS PDF.
2. **Evaluate Paths:** Loop through each field in the spec. Use JSONPath to grab the matching value from the user's data (e.g., `$.taxpayer.address.city`).
3. **Format & Align:** Apply formatting rules (like rounding to the nearest dollar). Calculate the text width and shrink the font size if it exceeds the bounding box.
4. **Stamp Canvas:** Draw the text onto the PDF using exact X/Y coordinates.
5. **Flatten:** Strip all interactive AcroForm widgets from the PDF.
6. **Output:** Return the final, flat PDF byte array.

### 4.2 Handling Dashed SSN Boxes (Combing)

The IRS uses dashed boxes for Social Security Numbers. If we print one long string, the numbers drift across the printed lines. We use a combing algorithm to split the string and position each segment.

1. Strip hyphens to get a clean 9-digit string.
2. Split into Area (3 digits), Group (2 digits), and Serial (4 digits).
3. Calculate physical offsets based on the bounding box width ($W_{box}$) and origin ($X_{box}$):
* **Area Code:** 
$$X_{part1} = X_{box} + 2.0$$

* **Group Code:** 
$$X_{part2} = X_{box} + (W_{box} \times 0.34)$$

* **Serial Code:** 
$$X_{part3} = X_{box} + (W_{box} \times 0.58)$$

4. Stamp each segment into its specific compartment.

### 4.3 Geometric Layout Engine

PDFs draw text from the bottom-left baseline. If we use the exact bottom of our bounding box ($Y_{box}$), letters like "g" and "y" will bleed into the border below. We add an optical offset:

$$Y_{text} = Y_{box} + 2.5$$

Horizontal alignment is calculated dynamically. For right-aligned text (like currency):

$$X_{origin} = (X_{box} + W_{box}) - W_{text} - 2.0$$

### 4.4 Data Models

We use immutable Java 17 records for thread safety.

```java
public record FormSpecification(
    String formId,
    int taxYear,
    int pages,
    PageDimensions pageDimensions,
    List<FieldAnnotation> fields
) {}

public record FieldAnnotation(
    String fieldId,
    String irsLineNumber,
    int page,
    Position position,
    FormatType format,
    TextAlignment alignment,
    String dataPath
) {}
```

## 5. Security & PDF Flattening

Interactive widgets left in the final PDF can obscure our stamped text depending on the user's PDF viewer. We must flatten the document before saving.

1. **Remove Catalog Dictionary:**
```java
document.getDocumentCatalog().getCOSObject().removeItem(COSName.ACRO_FORM);
```

2. **Purge Page Widgets:** Iterate through all pages and delete any `PDAnnotationWidget` objects.

## 6. Complete Java Class Definitions (JDK 17+)

All domain classes are implemented as immutable Java 17 records with Jackson annotations and defensive defaulting constructors to guarantee thread safety across concurrent rendering processes.

### 6.1 `FormSpecification.java`
```java
package com.taxapp.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FormSpecification(
    @JsonProperty("formId") String formId,
    @JsonProperty("taxYear") int taxYear,
    @JsonProperty("pages") int pages,
    @JsonProperty("unit") String unit,
    @JsonProperty("pageDimensions") PageDimensions pageDimensions,
    @JsonProperty("fields") List<FieldAnnotation> fields
) {}
```

### 6.2 `FieldAnnotation.java`
```java
package com.taxapp.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FieldAnnotation(
    @JsonProperty("fieldId") String fieldId,
    @JsonProperty("irsLineNumber") String irsLineNumber,
    @JsonProperty("page") int page,
    @JsonProperty("position") Position position,
    @JsonProperty("format") FormatType format,
    @JsonProperty("alignment") TextAlignment alignment,
    @JsonProperty("dataPath") String dataPath
) {
    /**
     * Compact constructor providing intelligent default alignments when omitted.
     */
    public FieldAnnotation {
        if (alignment == null) {
            if (format == FormatType.CURRENCY_ROUNDED || format == FormatType.CURRENCY_EXACT) {
                alignment = TextAlignment.RIGHT;
            } else if (format == FormatType.CHECKBOX_BOOLEAN) {
                alignment = TextAlignment.CENTER;
            } else {
                alignment = TextAlignment.LEFT;
            }
        }
    }
}
```

### 6.3 `Position.java` & `PageDimensions.java`
```java
package com.taxapp.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Position(
    @JsonProperty("x") double x,
    @JsonProperty("y") double y,
    @JsonProperty("width") double width,
    @JsonProperty("height") double height
) {}
```

```java
package com.taxapp.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PageDimensions(
    @JsonProperty("width") double width,
    @JsonProperty("height") double height
) {}
```

### 6.4 `FormatType.java` & `TextAlignment.java`
```java
package com.taxapp.model;

public enum FormatType {
    UPPERCASE_TEXT,
    CURRENCY_ROUNDED,
    CURRENCY_EXACT,
    SSN_UNMASKED,
    SSN_MASKED,
    SSN_COMBED,
    DATE_MMDDYYYY,
    CHECKBOX_BOOLEAN
}
```

```java
package com.taxapp.model;

public enum TextAlignment {
    LEFT,
    CENTER,
    RIGHT
}
```

---

## 7. Reference JSON Artifacts

### 7.1 Dynamic Taxpayer Payload (`sample_payer_data.json`)

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
      "aptNo": "SUITE 400",
      "zipCode": "78701"
    },
    "occupation": "SOFTWARE ARCHITECT",
    "phone": "(512) 555-0199",
    "identityProtectionPin": "987654",
    "signatureDate": "04/15/2026"
  },
  "filingStatus": {
    "isSingle": true,
    "isMarriedFilingJointly": false
  },
  "digitalAssets": {
    "answeredNo": true
  },
  "dependents": [
    {
      "firstName": "ALEX",
      "lastName": "DOE",
      "ssn": "987-65-4321",
      "relationship": "SON"
    },
    {
      "firstName": "SARAH",
      "lastName": "DOE",
      "ssn": "987-65-4322",
      "relationship": "DAUGHTER"
    },
    {
      "firstName": "MICHAEL",
      "lastName": "DOE",
      "ssn": "987-65-4323",
      "relationship": "SON"
    }
  ],
  "income": {
    "w2Wages": 115000.50,
    "householdEmployeeWages": 4320.75,
    "totalEarnedIncome1z": 115000.50,
    "totalIncome": 115000.50
  },
  "tax": {
    "adjustedGrossIncome": 115000.50,
    "taxableIncome": 95250.50,
    "totalTax": 16420.00
  },
  "payments": {
    "totalWithholding": 19500.00,
    "formerSpouseSsn": "999-88-7766"
  },
  "refund": {
    "overpaidAmount": 3080.00
  }
}
```

### 7.2 Form Annotation Specification (`form1040_2025_spec.json`)

```json
{
  "formId": "IRS-1040",
  "taxYear": 2025,
  "pages": 2,
  "unit": "POINTS",
  "pageDimensions": {
    "width": 612.0,
    "height": 792.0
  },
  "fields": [
    {
      "fieldId": "tax_year_beginning_date",
      "irsLineNumber": "Header",
      "page": 1,
      "position": { "x": 355.0, "y": 742.0, "width": 65.0, "height": 10.0 },
      "format": "DATE_MMDDYYYY",
      "alignment": "LEFT",
      "dataPath": "$.header.fiscalYearBeginningDate"
    },
    {
      "fieldId": "tax_year_ending_date",
      "irsLineNumber": "Header",
      "page": 1,
      "position": { "x": 480.0, "y": 742.0, "width": 65.0, "height": 10.0 },
      "format": "DATE_MMDDYYYY",
      "alignment": "LEFT",
      "dataPath": "$.header.fiscalYearEndingDate"
    },
    {
      "fieldId": "header_special_status_301_9100_2",
      "irsLineNumber": "Header",
      "page": 1,
      "position": { "x": 130.0, "y": 754.0, "width": 8.0, "height": 8.0 },
      "format": "CHECKBOX_BOOLEAN",
      "alignment": "CENTER",
      "dataPath": "$.header.specialStatus301_9100_2"
    },
    {
      "fieldId": "header_special_status_combat_zone",
      "irsLineNumber": "Header",
      "page": 1,
      "position": { "x": 195.0, "y": 754.0, "width": 8.0, "height": 8.0 },
      "format": "CHECKBOX_BOOLEAN",
      "alignment": "CENTER",
      "dataPath": "$.header.specialStatusCombatZone"
    },
    {
      "fieldId": "header_special_status_other",
      "irsLineNumber": "Header",
      "page": 1,
      "position": { "x": 245.0, "y": 754.0, "width": 8.0, "height": 8.0 },
      "format": "CHECKBOX_BOOLEAN",
      "alignment": "CENTER",
      "dataPath": "$.header.specialStatusOther"
    },
    {
      "fieldId": "taxpayer_first_name_mi",
      "irsLineNumber": "Header",
      "page": 1,
      "position": { "x": 36.0, "y": 684.0, "width": 215.25, "height": 14.0 },
      "format": "UPPERCASE_TEXT",
      "alignment": "LEFT",
      "dataPath": "$.taxpayer.firstNameAndInitial"
    },
    {
      "fieldId": "taxpayer_last_name",
      "irsLineNumber": "Header",
      "page": 1,
      "position": { "x": 253.0, "y": 684.0, "width": 214.25, "height": 14.0 },
      "format": "UPPERCASE_TEXT",
      "alignment": "LEFT",
      "dataPath": "$.taxpayer.lastName"
    },
    {
      "fieldId": "taxpayer_ssn",
      "irsLineNumber": "Header",
      "page": 1,
      "position": { "x": 469.0, "y": 684.0, "width": 107.0, "height": 14.0 },
      "format": "SSN_COMBED",
      "alignment": "LEFT",
      "dataPath": "$.taxpayer.ssn"
    },
    {
      "fieldId": "taxpayer_date_of_death",
      "irsLineNumber": "Header",
      "page": 1,
      "position": { "x": 469.0, "y": 708.0, "width": 107.0, "height": 12.0 },
      "format": "DATE_MMDDYYYY",
      "alignment": "LEFT",
      "dataPath": "$.taxpayer.dateOfDeath"
    },
    {
      "fieldId": "spouse_first_name_mi",
      "irsLineNumber": "Header",
      "page": 1,
      "position": { "x": 36.0, "y": 660.0, "width": 215.25, "height": 14.0 },
      "format": "UPPERCASE_TEXT",
      "alignment": "LEFT",
      "dataPath": "$.spouse.firstNameAndInitial"
    },
    {
      "fieldId": "spouse_last_name",
      "irsLineNumber": "Header",
      "page": 1,
      "position": { "x": 253.0, "y": 660.0, "width": 214.25, "height": 14.0 },
      "format": "UPPERCASE_TEXT",
      "alignment": "LEFT",
      "dataPath": "$.spouse.lastName"
    },
    {
      "fieldId": "spouse_ssn",
      "irsLineNumber": "Header",
      "page": 1,
      "position": { "x": 469.0, "y": 660.0, "width": 107.0, "height": 14.0 },
      "format": "SSN_COMBED",
      "alignment": "LEFT",
      "dataPath": "$.spouse.ssn"
    },
    {
      "fieldId": "spouse_date_of_death",
      "irsLineNumber": "Header",
      "page": 1,
      "position": { "x": 469.0, "y": 648.0, "width": 107.0, "height": 12.0 },
      "format": "DATE_MMDDYYYY",
      "alignment": "LEFT",
      "dataPath": "$.spouse.dateOfDeath"
    },
    {
      "fieldId": "home_address_street",
      "irsLineNumber": "Header",
      "page": 1,
      "position": { "x": 36.0, "y": 636.0, "width": 380.85, "height": 14.0 },
      "format": "UPPERCASE_TEXT",
      "alignment": "LEFT",
      "dataPath": "$.taxpayer.address.street"
    },
    {
      "fieldId": "home_address_apt",
      "irsLineNumber": "Header",
      "page": 1,
      "position": { "x": 418.6, "y": 636.0, "width": 48.65, "height": 14.0 },
      "format": "UPPERCASE_TEXT",
      "alignment": "LEFT",
      "dataPath": "$.taxpayer.address.aptNo"
    },
    {
      "fieldId": "home_address_city",
      "irsLineNumber": "Header",
      "page": 1,
      "position": { "x": 36.0, "y": 612.0, "width": 294.45, "height": 14.0 },
      "format": "UPPERCASE_TEXT",
      "alignment": "LEFT",
      "dataPath": "$.taxpayer.address.city"
    },
    {
      "fieldId": "home_address_state",
      "irsLineNumber": "Header",
      "page": 1,
      "position": { "x": 332.2, "y": 612.0, "width": 63.05, "height": 14.0 },
      "format": "UPPERCASE_TEXT",
      "alignment": "LEFT",
      "dataPath": "$.taxpayer.address.state"
    },
    {
      "fieldId": "home_address_zip",
      "irsLineNumber": "Header",
      "page": 1,
      "position": { "x": 397.0, "y": 612.0, "width": 70.25, "height": 14.0 },
      "format": "UPPERCASE_TEXT",
      "alignment": "LEFT",
      "dataPath": "$.taxpayer.address.zipCode"
    },
    {
      "fieldId": "header_residency_main_home_us",
      "irsLineNumber": "Header",
      "page": 1,
      "position": { "x": 485.0, "y": 624.0, "width": 8.0, "height": 8.0 },
      "format": "CHECKBOX_BOOLEAN",
      "alignment": "CENTER",
      "dataPath": "$.taxpayer.mainHomeInUsMoreThanHalfYear"
    },
    {
      "fieldId": "foreign_country_name",
      "irsLineNumber": "Header",
      "page": 1,
      "position": { "x": 36.0, "y": 598.0, "width": 200.0, "height": 12.0 },
      "format": "UPPERCASE_TEXT",
      "alignment": "LEFT",
      "dataPath": "$.taxpayer.address.foreignCountry"
    },
    {
      "fieldId": "foreign_province_state",
      "irsLineNumber": "Header",
      "page": 1,
      "position": { "x": 242.0, "y": 598.0, "width": 180.0, "height": 12.0 },
      "format": "UPPERCASE_TEXT",
      "alignment": "LEFT",
      "dataPath": "$.taxpayer.address.foreignProvince"
    },
    {
      "fieldId": "foreign_postal_code",
      "irsLineNumber": "Header",
      "page": 1,
      "position": { "x": 428.0, "y": 598.0, "width": 100.0, "height": 12.0 },
      "format": "UPPERCASE_TEXT",
      "alignment": "LEFT",
      "dataPath": "$.taxpayer.address.foreignPostalCode"
    },
    {
      "fieldId": "filing_status_single",
      "irsLineNumber": "Filing Status",
      "page": 1,
      "position": { "x": 97.6, "y": 578.0, "width": 8.0, "height": 8.0 },
      "format": "CHECKBOX_BOOLEAN",
      "alignment": "CENTER",
      "dataPath": "$.filingStatus.isSingle"
    },
    {
      "fieldId": "filing_status_mfj",
      "irsLineNumber": "Filing Status",
      "page": 1,
      "position": { "x": 97.6, "y": 566.0, "width": 8.0, "height": 8.0 },
      "format": "CHECKBOX_BOOLEAN",
      "alignment": "CENTER",
      "dataPath": "$.filingStatus.isMarriedFilingJointly"
    },
    {
      "fieldId": "filing_status_mfs",
      "irsLineNumber": "Filing Status",
      "page": 1,
      "position": { "x": 97.6, "y": 554.0, "width": 8.0, "height": 8.0 },
      "format": "CHECKBOX_BOOLEAN",
      "alignment": "CENTER",
      "dataPath": "$.filingStatus.isMarriedFilingSeparately"
    },
    {
      "fieldId": "filing_status_hoh",
      "irsLineNumber": "Filing Status",
      "page": 1,
      "position": { "x": 349.6, "y": 578.0, "width": 8.0, "height": 8.0 },
      "format": "CHECKBOX_BOOLEAN",
      "alignment": "CENTER",
      "dataPath": "$.filingStatus.isHeadOfHousehold"
    },
    {
      "fieldId": "filing_status_qss",
      "irsLineNumber": "Filing Status",
      "page": 1,
      "position": { "x": 349.6, "y": 566.0, "width": 8.0, "height": 8.0 },
      "format": "CHECKBOX_BOOLEAN",
      "alignment": "CENTER",
      "dataPath": "$.filingStatus.isQualifyingSurvivingSpouse"
    },
    {
      "fieldId": "filing_status_mfs_spouse_name",
      "irsLineNumber": "Filing Status",
      "page": 1,
      "position": { "x": 245.0, "y": 554.0, "width": 330.0, "height": 12.0 },
      "format": "UPPERCASE_TEXT",
      "alignment": "LEFT",
      "dataPath": "$.filingStatus.mfsSpouseName"
    },
    {
      "fieldId": "filing_status_qualifying_person_name",
      "irsLineNumber": "Filing Status",
      "page": 1,
      "position": { "x": 245.0, "y": 542.0, "width": 330.0, "height": 12.0 },
      "format": "UPPERCASE_TEXT",
      "alignment": "LEFT",
      "dataPath": "$.filingStatus.qualifyingPersonName"
    },
    {
      "fieldId": "filing_status_nra_spouse_election_checkbox",
      "irsLineNumber": "Filing Status",
      "page": 1,
      "position": { "x": 36.0, "y": 530.0, "width": 8.0, "height": 8.0 },
      "format": "CHECKBOX_BOOLEAN",
      "alignment": "CENTER",
      "dataPath": "$.filingStatus.treatNraSpouseAsResidentElection"
    },
    {
      "fieldId": "filing_status_nra_spouse_name",
      "irsLineNumber": "Filing Status",
      "page": 1,
      "position": { "x": 350.0, "y": 530.0, "width": 220.0, "height": 12.0 },
      "format": "UPPERCASE_TEXT",
      "alignment": "LEFT",
      "dataPath": "$.filingStatus.treatNraSpouseAsResidentSpouseName"
    },
    {
      "fieldId": "digital_assets_yes",
      "irsLineNumber": "Digital Assets",
      "page": 1,
      "position": { "x": 518.4, "y": 497.0, "width": 8.0, "height": 8.0 },
      "format": "CHECKBOX_BOOLEAN",
      "alignment": "CENTER",
      "dataPath": "$.digitalAssets.answeredYes"
    },
    {
      "fieldId": "digital_assets_no",
      "irsLineNumber": "Digital Assets",
      "page": 1,
      "position": { "x": 554.4, "y": 497.0, "width": 8.0, "height": 8.0 },
      "format": "CHECKBOX_BOOLEAN",
      "alignment": "CENTER",
      "dataPath": "$.digitalAssets.answeredNo"
    },
    {
      "fieldId": "dep1_first_name",
      "irsLineNumber": "Dependents",
      "page": 1,
      "position": { "x": 145.0, "y": 471.0, "width": 106.0, "height": 11.0 },
      "format": "UPPERCASE_TEXT",
      "alignment": "LEFT",
      "dataPath": "$.dependents[0].firstName"
    },
    {
      "fieldId": "dep1_last_name",
      "irsLineNumber": "Dependents",
      "page": 1,
      "position": { "x": 145.0, "y": 459.0, "width": 106.0, "height": 11.0 },
      "format": "UPPERCASE_TEXT",
      "alignment": "LEFT",
      "dataPath": "$.dependents[0].lastName"
    },
    {
      "fieldId": "dep1_ssn",
      "irsLineNumber": "Dependents",
      "page": 1,
      "position": { "x": 145.0, "y": 447.0, "width": 106.0, "height": 11.0 },
      "format": "SSN_COMBED",
      "alignment": "LEFT",
      "dataPath": "$.dependents[0].ssn"
    },
    {
      "fieldId": "dep1_relationship",
      "irsLineNumber": "Dependents",
      "page": 1,
      "position": { "x": 145.0, "y": 435.0, "width": 106.0, "height": 11.0 },
      "format": "UPPERCASE_TEXT",
      "alignment": "LEFT",
      "dataPath": "$.dependents[0].relationship"
    },
    {
      "fieldId": "dep1_lived_with_you_more_than_half_year",
      "irsLineNumber": "Dependents",
      "page": 1,
      "position": { "x": 163.0, "y": 418.0, "width": 8.0, "height": 8.0 },
      "format": "CHECKBOX_BOOLEAN",
      "alignment": "CENTER",
      "dataPath": "$.dependents[0].livedWithYouMoreThanHalfYear"
    },
    {
      "fieldId": "dep1_lived_in_us",
      "irsLineNumber": "Dependents",
      "page": 1,
      "position": { "x": 163.0, "y": 405.0, "width": 8.0, "height": 8.0 },
      "format": "CHECKBOX_BOOLEAN",
      "alignment": "CENTER",
      "dataPath": "$.dependents[0].livedInUs"
    },
    {
      "fieldId": "dep1_full_time_student",
      "irsLineNumber": "Dependents",
      "page": 1,
      "position": { "x": 150.0, "y": 388.0, "width": 8.0, "height": 8.0 },
      "format": "CHECKBOX_BOOLEAN",
      "alignment": "CENTER",
      "dataPath": "$.dependents[0].isFullTimeStudent"
    },
    {
      "fieldId": "dep1_permanently_disabled",
      "irsLineNumber": "Dependents",
      "page": 1,
      "position": { "x": 201.0, "y": 388.0, "width": 8.0, "height": 8.0 },
      "format": "CHECKBOX_BOOLEAN",
      "alignment": "CENTER",
      "dataPath": "$.dependents[0].isPermanentlyDisabled"
    },
    {
      "fieldId": "dep1_child_tax_credit",
      "irsLineNumber": "Dependents",
      "page": 1,
      "position": { "x": 150.0, "y": 370.0, "width": 8.0, "height": 8.0 },
      "format": "CHECKBOX_BOOLEAN",
      "alignment": "CENTER",
      "dataPath": "$.dependents[0].qualifiesChildTaxCredit"
    },
    {
      "fieldId": "dep1_other_dep_credit",
      "irsLineNumber": "Dependents",
      "page": 1,
      "position": { "x": 201.0, "y": 370.0, "width": 8.0, "height": 8.0 },
      "format": "CHECKBOX_BOOLEAN",
      "alignment": "CENTER",
      "dataPath": "$.dependents[0].qualifiesCreditOtherDependents"
    },
    {
      "fieldId": "dep2_first_name",
      "irsLineNumber": "Dependents",
      "page": 1,
      "position": { "x": 253.0, "y": 471.0, "width": 106.0, "height": 11.0 },
      "format": "UPPERCASE_TEXT",
      "alignment": "LEFT",
      "dataPath": "$.dependents[1].firstName"
    },
    {
      "fieldId": "dep2_last_name",
      "irsLineNumber": "Dependents",
      "page": 1,
      "position": { "x": 253.0, "y": 459.0, "width": 106.0, "height": 11.0 },
      "format": "UPPERCASE_TEXT",
      "alignment": "LEFT",
      "dataPath": "$.dependents[1].lastName"
    },
    {
      "fieldId": "dep2_ssn",
      "irsLineNumber": "Dependents",
      "page": 1,
      "position": { "x": 253.0, "y": 447.0, "width": 106.0, "height": 11.0 },
      "format": "SSN_COMBED",
      "alignment": "LEFT",
      "dataPath": "$.dependents[1].ssn"
    },
    {
      "fieldId": "dep2_relationship",
      "irsLineNumber": "Dependents",
      "page": 1,
      "position": { "x": 253.0, "y": 435.0, "width": 106.0, "height": 11.0 },
      "format": "UPPERCASE_TEXT",
      "alignment": "LEFT",
      "dataPath": "$.dependents[1].relationship"
    },
    {
      "fieldId": "dep2_lived_with_you_more_than_half_year",
      "irsLineNumber": "Dependents",
      "page": 1,
      "position": { "x": 271.0, "y": 418.0, "width": 8.0, "height": 8.0 },
      "format": "CHECKBOX_BOOLEAN",
      "alignment": "CENTER",
      "dataPath": "$.dependents[1].livedWithYouMoreThanHalfYear"
    },
    {
      "fieldId": "dep2_lived_in_us",
      "irsLineNumber": "Dependents",
      "page": 1,
      "position": { "x": 271.0, "y": 405.0, "width": 8.0, "height": 8.0 },
      "format": "CHECKBOX_BOOLEAN",
      "alignment": "CENTER",
      "dataPath": "$.dependents[1].livedInUs"
    },
    {
      "fieldId": "dep2_full_time_student",
      "irsLineNumber": "Dependents",
      "page": 1,
      "position": { "x": 258.0, "y": 388.0, "width": 8.0, "height": 8.0 },
      "format": "CHECKBOX_BOOLEAN",
      "alignment": "CENTER",
      "dataPath": "$.dependents[1].isFullTimeStudent"
    },
    {
      "fieldId": "dep2_permanently_disabled",
      "irsLineNumber": "Dependents",
      "page": 1,
      "position": { "x": 309.0, "y": 388.0, "width": 8.0, "height": 8.0 },
      "format": "CHECKBOX_BOOLEAN",
      "alignment": "CENTER",
      "dataPath": "$.dependents[1].isPermanentlyDisabled"
    },
    {
      "fieldId": "dep2_child_tax_credit",
      "irsLineNumber": "Dependents",
      "page": 1,
      "position": { "x": 258.0, "y": 370.0, "width": 8.0, "height": 8.0 },
      "format": "CHECKBOX_BOOLEAN",
      "alignment": "CENTER",
      "dataPath": "$.dependents[1].qualifiesChildTaxCredit"
    },
    {
      "fieldId": "dep2_other_dep_credit",
      "irsLineNumber": "Dependents",
      "page": 1,
      "position": { "x": 309.0, "y": 370.0, "width": 8.0, "height": 8.0 },
      "format": "CHECKBOX_BOOLEAN",
      "alignment": "CENTER",
      "dataPath": "$.dependents[1].qualifiesCreditOtherDependents"
    },
    {
      "fieldId": "dep3_first_name",
      "irsLineNumber": "Dependents",
      "page": 1,
      "position": { "x": 361.0, "y": 471.0, "width": 106.0, "height": 11.0 },
      "format": "UPPERCASE_TEXT",
      "alignment": "LEFT",
      "dataPath": "$.dependents[2].firstName"
    },
    {
      "fieldId": "dep3_last_name",
      "irsLineNumber": "Dependents",
      "page": 1,
      "position": { "x": 361.0, "y": 459.0, "width": 106.0, "height": 11.0 },
      "format": "UPPERCASE_TEXT",
      "alignment": "LEFT",
      "dataPath": "$.dependents[2].lastName"
    },
    {
      "fieldId": "dep3_ssn",
      "irsLineNumber": "Dependents",
      "page": 1,
      "position": { "x": 361.0, "y": 447.0, "width": 106.0, "height": 11.0 },
      "format": "SSN_COMBED",
      "alignment": "LEFT",
      "dataPath": "$.dependents[2].ssn"
    },
    {
      "fieldId": "dep3_relationship",
      "irsLineNumber": "Dependents",
      "page": 1,
      "position": { "x": 361.0, "y": 435.0, "width": 106.0, "height": 11.0 },
      "format": "UPPERCASE_TEXT",
      "alignment": "LEFT",
      "dataPath": "$.dependents[2].relationship"
    },
    {
      "fieldId": "dep3_lived_with_you_more_than_half_year",
      "irsLineNumber": "Dependents",
      "page": 1,
      "position": { "x": 379.0, "y": 418.0, "width": 8.0, "height": 8.0 },
      "format": "CHECKBOX_BOOLEAN",
      "alignment": "CENTER",
      "dataPath": "$.dependents[2].livedWithYouMoreThanHalfYear"
    },
    {
      "fieldId": "dep3_lived_in_us",
      "irsLineNumber": "Dependents",
      "page": 1,
      "position": { "x": 379.0, "y": 405.0, "width": 8.0, "height": 8.0 },
      "format": "CHECKBOX_BOOLEAN",
      "alignment": "CENTER",
      "dataPath": "$.dependents[2].livedInUs"
    },
    {
      "fieldId": "dep3_full_time_student",
      "irsLineNumber": "Dependents",
      "page": 1,
      "position": { "x": 366.0, "y": 388.0, "width": 8.0, "height": 8.0 },
      "format": "CHECKBOX_BOOLEAN",
      "alignment": "CENTER",
      "dataPath": "$.dependents[2].isFullTimeStudent"
    },
    {
      "fieldId": "dep3_permanently_disabled",
      "irsLineNumber": "Dependents",
      "page": 1,
      "position": { "x": 417.0, "y": 388.0, "width": 8.0, "height": 8.0 },
      "format": "CHECKBOX_BOOLEAN",
      "alignment": "CENTER",
      "dataPath": "$.dependents[2].isPermanentlyDisabled"
    },
    {
      "fieldId": "dep3_child_tax_credit",
      "irsLineNumber": "Dependents",
      "page": 1,
      "position": { "x": 366.0, "y": 370.0, "width": 8.0, "height": 8.0 },
      "format": "CHECKBOX_BOOLEAN",
      "alignment": "CENTER",
      "dataPath": "$.dependents[2].qualifiesChildTaxCredit"
    },
    {
      "fieldId": "dep3_other_dep_credit",
      "irsLineNumber": "Dependents",
      "page": 1,
      "position": { "x": 417.0, "y": 370.0, "width": 8.0, "height": 8.0 },
      "format": "CHECKBOX_BOOLEAN",
      "alignment": "CENTER",
      "dataPath": "$.dependents[2].qualifiesCreditOtherDependents"
    },
    {
      "fieldId": "dep4_first_name",
      "irsLineNumber": "Dependents",
      "page": 1,
      "position": { "x": 469.0, "y": 471.0, "width": 106.0, "height": 11.0 },
      "format": "UPPERCASE_TEXT",
      "alignment": "LEFT",
      "dataPath": "$.dependents[3].firstName"
    },
    {
      "fieldId": "dep4_last_name",
      "irsLineNumber": "Dependents",
      "page": 1,
      "position": { "x": 469.0, "y": 459.0, "width": 106.0, "height": 11.0 },
      "format": "UPPERCASE_TEXT",
      "alignment": "LEFT",
      "dataPath": "$.dependents[3].lastName"
    },
    {
      "fieldId": "dep4_ssn",
      "irsLineNumber": "Dependents",
      "page": 1,
      "position": { "x": 469.0, "y": 447.0, "width": 106.0, "height": 11.0 },
      "format": "SSN_COMBED",
      "alignment": "LEFT",
      "dataPath": "$.dependents[3].ssn"
    },
    {
      "fieldId": "dep4_relationship",
      "irsLineNumber": "Dependents",
      "page": 1,
      "position": { "x": 469.0, "y": 435.0, "width": 106.0, "height": 11.0 },
      "format": "UPPERCASE_TEXT",
      "alignment": "LEFT",
      "dataPath": "$.dependents[3].relationship"
    },
    {
      "fieldId": "dep4_lived_with_you_more_than_half_year",
      "irsLineNumber": "Dependents",
      "page": 1,
      "position": { "x": 487.0, "y": 418.0, "width": 8.0, "height": 8.0 },
      "format": "CHECKBOX_BOOLEAN",
      "alignment": "CENTER",
      "dataPath": "$.dependents[3].livedWithYouMoreThanHalfYear"
    },
    {
      "fieldId": "dep4_lived_in_us",
      "irsLineNumber": "Dependents",
      "page": 1,
      "position": { "x": 487.0, "y": 405.0, "width": 8.0, "height": 8.0 },
      "format": "CHECKBOX_BOOLEAN",
      "alignment": "CENTER",
      "dataPath": "$.dependents[3].livedInUs"
    },
    {
      "fieldId": "dep4_full_time_student",
      "irsLineNumber": "Dependents",
      "page": 1,
      "position": { "x": 474.0, "y": 388.0, "width": 8.0, "height": 8.0 },
      "format": "CHECKBOX_BOOLEAN",
      "alignment": "CENTER",
      "dataPath": "$.dependents[3].isFullTimeStudent"
    },
    {
      "fieldId": "dep4_permanently_disabled",
      "irsLineNumber": "Dependents",
      "page": 1,
      "position": { "x": 525.0, "y": 388.0, "width": 8.0, "height": 8.0 },
      "format": "CHECKBOX_BOOLEAN",
      "alignment": "CENTER",
      "dataPath": "$.dependents[3].isPermanentlyDisabled"
    },
    {
      "fieldId": "dep4_child_tax_credit",
      "irsLineNumber": "Dependents",
      "page": 1,
      "position": { "x": 474.0, "y": 370.0, "width": 8.0, "height": 8.0 },
      "format": "CHECKBOX_BOOLEAN",
      "alignment": "CENTER",
      "dataPath": "$.dependents[3].qualifiesChildTaxCredit"
    },
    {
      "fieldId": "dep4_other_dep_credit",
      "irsLineNumber": "Dependents",
      "page": 1,
      "position": { "x": 525.0, "y": 370.0, "width": 8.0, "height": 8.0 },
      "format": "CHECKBOX_BOOLEAN",
      "alignment": "CENTER",
      "dataPath": "$.dependents[3].qualifiesCreditOtherDependents"
    },
    {
      "fieldId": "more_than_four_dependents",
      "irsLineNumber": "Dependents",
      "page": 1,
      "position": { "x": 93.0, "y": 400.0, "width": 8.0, "height": 8.0 },
      "format": "CHECKBOX_BOOLEAN",
      "alignment": "CENTER",
      "dataPath": "$.dependentsOverview.moreThanFourDependents"
    },
    {
      "fieldId": "mfs_hoh_lived_apart_last_6_months",
      "irsLineNumber": "Filing Status",
      "page": 1,
      "position": { "x": 485.0, "y": 350.0, "width": 8.0, "height": 8.0 },
      "format": "CHECKBOX_BOOLEAN",
      "alignment": "CENTER",
      "dataPath": "$.filingStatus.livedApartFromSpouseLast6Months"
    },
    {
      "fieldId": "line_1a_w2_wages",
      "irsLineNumber": "1a",
      "page": 1,
      "position": { "x": 504.0, "y": 330.0, "width": 72.0, "height": 12.0 },
      "format": "CURRENCY_ROUNDED",
      "alignment": "RIGHT",
      "dataPath": "$.income.w2Wages"
    },
    {
      "fieldId": "line_1b_household_employee_wages",
      "irsLineNumber": "1b",
      "page": 1,
      "position": { "x": 504.0, "y": 318.0, "width": 72.0, "height": 12.0 },
      "format": "CURRENCY_EXACT",
      "alignment": "RIGHT",
      "dataPath": "$.income.householdEmployeeWages"
    },
    {
      "fieldId": "line_1c_unreported_tips",
      "irsLineNumber": "1c",
      "page": 1,
      "position": { "x": 504.0, "y": 306.0, "width": 72.0, "height": 12.0 },
      "format": "CURRENCY_ROUNDED",
      "alignment": "RIGHT",
      "dataPath": "$.income.unreportedTips"
    },
    {
      "fieldId": "line_1d_medicaid_waiver_payments",
      "irsLineNumber": "1d",
      "page": 1,
      "position": { "x": 504.0, "y": 294.0, "width": 72.0, "height": 12.0 },
      "format": "CURRENCY_ROUNDED",
      "alignment": "RIGHT",
      "dataPath": "$.income.medicaidWaiverPayments"
    },
    {
      "fieldId": "line_1e_dependent_care_benefits",
      "irsLineNumber": "1e",
      "page": 1,
      "position": { "x": 504.0, "y": 282.0, "width": 72.0, "height": 12.0 },
      "format": "CURRENCY_ROUNDED",
      "alignment": "RIGHT",
      "dataPath": "$.income.dependentCareBenefits"
    },
    {
      "fieldId": "line_1f_employer_adoption_benefits",
      "irsLineNumber": "1f",
      "page": 1,
      "position": { "x": 504.0, "y": 270.0, "width": 72.0, "height": 12.0 },
      "format": "CURRENCY_ROUNDED",
      "alignment": "RIGHT",
      "dataPath": "$.income.adoptionBenefits"
    },
    {
      "fieldId": "line_1g_form_8919_wages",
      "irsLineNumber": "1g",
      "page": 1,
      "position": { "x": 504.0, "y": 258.0, "width": 72.0, "height": 12.0 },
      "format": "CURRENCY_ROUNDED",
      "alignment": "RIGHT",
      "dataPath": "$.income.form8919Wages"
    },
    {
      "fieldId": "line_1h_other_earned_income_type",
      "irsLineNumber": "1h",
      "page": 1,
      "position": { "x": 230.0, "y": 246.0, "width": 260.0, "height": 12.0 },
      "format": "UPPERCASE_TEXT",
      "alignment": "LEFT",
      "dataPath": "$.income.otherEarnedIncomeType"
    },
    {
      "fieldId": "line_1h_other_earned_income_amount",
      "irsLineNumber": "1h",
      "page": 1,
      "position": { "x": 504.0, "y": 246.0, "width": 72.0, "height": 12.0 },
      "format": "CURRENCY_ROUNDED",
      "alignment": "RIGHT",
      "dataPath": "$.income.otherEarnedIncomeAmount"
    },
    {
      "fieldId": "line_1i_nontaxable_combat_pay",
      "irsLineNumber": "1i",
      "page": 1,
      "position": { "x": 410.4, "y": 234.0, "width": 71.25, "height": 12.0 },
      "format": "CURRENCY_ROUNDED",
      "alignment": "RIGHT",
      "dataPath": "$.income.nontaxableCombatPay"
    },
    {
      "fieldId": "line_1z_total_earned_income",
      "irsLineNumber": "1z",
      "page": 1,
      "position": { "x": 504.0, "y": 222.0, "width": 72.0, "height": 12.0 },
      "format": "CURRENCY_ROUNDED",
      "alignment": "RIGHT",
      "dataPath": "$.income.totalEarnedIncome1z"
    },
    {
      "fieldId": "line_2a_tax_exempt_interest",
      "irsLineNumber": "2a",
      "page": 1,
      "position": { "x": 410.4, "y": 210.0, "width": 71.25, "height": 12.0 },
      "format": "CURRENCY_ROUNDED",
      "alignment": "RIGHT",
      "dataPath": "$.income.taxExemptInterest"
    },
    {
      "fieldId": "line_2b_taxable_interest",
      "irsLineNumber": "2b",
      "page": 1,
      "position": { "x": 504.0, "y": 210.0, "width": 72.0, "height": 12.0 },
      "format": "CURRENCY_ROUNDED",
      "alignment": "RIGHT",
      "dataPath": "$.income.taxableInterest"
    },
    {
      "fieldId": "line_3a_qualified_dividends",
      "irsLineNumber": "3a",
      "page": 1,
      "position": { "x": 410.4, "y": 198.0, "width": 71.25, "height": 12.0 },
      "format": "CURRENCY_ROUNDED",
      "alignment": "RIGHT",
      "dataPath": "$.income.qualifiedDividends"
    },
    {
      "fieldId": "line_3b_ordinary_dividends",
      "irsLineNumber": "3b",
      "page": 1,
      "position": { "x": 504.0, "y": 198.0, "width": 72.0, "height": 12.0 },
      "format": "CURRENCY_ROUNDED",
      "alignment": "RIGHT",
      "dataPath": "$.income.ordinaryDividends"
    },
    {
      "fieldId": "line_3c_child_dividends_in_3a_checkbox",
      "irsLineNumber": "3c",
      "page": 1,
      "position": { "x": 430.0, "y": 186.0, "width": 8.0, "height": 8.0 },
      "format": "CHECKBOX_BOOLEAN",
      "alignment": "CENTER",
      "dataPath": "$.income.childDividendsIncludedIn3a"
    },
    {
      "fieldId": "line_3c_child_dividends_in_3b_checkbox",
      "irsLineNumber": "3c",
      "page": 1,
      "position": { "x": 470.0, "y": 186.0, "width": 8.0, "height": 8.0 },
      "format": "CHECKBOX_BOOLEAN",
      "alignment": "CENTER",
      "dataPath": "$.income.childDividendsIncludedIn3b"
    },
    {
      "fieldId": "line_4a_ira_distributions_total",
      "irsLineNumber": "4a",
      "page": 1,
      "position": { "x": 410.4, "y": 174.0, "width": 71.25, "height": 12.0 },
      "format": "CURRENCY_ROUNDED",
      "alignment": "RIGHT",
      "dataPath": "$.income.iraTotalDistributions"
    },
    {
      "fieldId": "line_4b_ira_taxable_amount",
      "irsLineNumber": "4b",
      "page": 1,
      "position": { "x": 504.0, "y": 174.0, "width": 72.0, "height": 12.0 },
      "format": "CURRENCY_ROUNDED",
      "alignment": "RIGHT",
      "dataPath": "$.income.iraTaxableAmount"
    },
    {
      "fieldId": "line_4c_ira_rollover_checkbox",
      "irsLineNumber": "4c",
      "page": 1,
      "position": { "x": 440.0, "y": 174.0, "width": 8.0, "height": 8.0 },
      "format": "CHECKBOX_BOOLEAN",
      "alignment": "CENTER",
      "dataPath": "$.income.iraRollover"
    },
    {
      "fieldId": "line_4c_ira_qcd_checkbox",
      "irsLineNumber": "4c",
      "page": 1,
      "position": { "x": 462.0, "y": 174.0, "width": 8.0, "height": 8.0 },
      "format": "CHECKBOX_BOOLEAN",
      "alignment": "CENTER",
      "dataPath": "$.income.iraQcd"
    },
    {
      "fieldId": "line_4c_ira_other_checkbox",
      "irsLineNumber": "4c",
      "page": 1,
      "position": { "x": 485.0, "y": 174.0, "width": 8.0, "height": 8.0 },
      "format": "CHECKBOX_BOOLEAN",
      "alignment": "CENTER",
      "dataPath": "$.income.iraOther"
    },
    {
      "fieldId": "line_5a_pensions_total",
      "irsLineNumber": "5a",
      "page": 1,
      "position": { "x": 410.4, "y": 150.0, "width": 71.25, "height": 12.0 },
      "format": "CURRENCY_ROUNDED",
      "alignment": "RIGHT",
      "dataPath": "$.income.pensionsTotalAmount"
    },
    {
      "fieldId": "line_5b_pensions_taxable_amount",
      "irsLineNumber": "5b",
      "page": 1,
      "position": { "x": 504.0, "y": 150.0, "width": 72.0, "height": 12.0 },
      "format": "CURRENCY_ROUNDED",
      "alignment": "RIGHT",
      "dataPath": "$.income.pensionsTaxableAmount"
    },
    {
      "fieldId": "line_5c_pension_rollover_checkbox",
      "irsLineNumber": "5c",
      "page": 1,
      "position": { "x": 440.0, "y": 150.0, "width": 8.0, "height": 8.0 },
      "format": "CHECKBOX_BOOLEAN",
      "alignment": "CENTER",
      "dataPath": "$.income.pensionRollover"
    },
    {
      "fieldId": "line_5c_pension_pso_checkbox",
      "irsLineNumber": "5c",
      "page": 1,
      "position": { "x": 462.0, "y": 150.0, "width": 8.0, "height": 8.0 },
      "format": "CHECKBOX_BOOLEAN",
      "alignment": "CENTER",
      "dataPath": "$.income.pensionPso"
    },
    {
      "fieldId": "line_5c_pension_other_checkbox",
      "irsLineNumber": "5c",
      "page": 1,
      "position": { "x": 485.0, "y": 150.0, "width": 8.0, "height": 8.0 },
      "format": "CHECKBOX_BOOLEAN",
      "alignment": "CENTER",
      "dataPath": "$.income.pensionOther"
    },
    {
      "fieldId": "line_6a_social_security_total",
      "irsLineNumber": "6a",
      "page": 1,
      "position": { "x": 410.4, "y": 126.0, "width": 71.25, "height": 12.0 },
      "format": "CURRENCY_ROUNDED",
      "alignment": "RIGHT",
      "dataPath": "$.income.socialSecurityTotalBenefits"
    },
    {
      "fieldId": "line_6b_taxable_social_security",
      "irsLineNumber": "6b",
      "page": 1,
      "position": { "x": 504.0, "y": 126.0, "width": 72.0, "height": 12.0 },
      "format": "CURRENCY_ROUNDED",
      "alignment": "RIGHT",
      "dataPath": "$.income.taxableSocialSecurity"
    },
    {
      "fieldId": "line_6c_lump_sum_election",
      "irsLineNumber": "6c",
      "page": 1,
      "position": { "x": 485.0, "y": 126.0, "width": 8.0, "height": 8.0 },
      "format": "CHECKBOX_BOOLEAN",
      "alignment": "CENTER",
      "dataPath": "$.income.lumpSumElectionMethodUsed"
    },
    {
      "fieldId": "line_6d_mfs_lived_apart_social_security",
      "irsLineNumber": "6d",
      "page": 1,
      "position": { "x": 485.0, "y": 114.0, "width": 8.0, "height": 8.0 },
      "format": "CHECKBOX_BOOLEAN",
      "alignment": "CENTER",
      "dataPath": "$.income.mfsLivedApartAllYearSocialSecurity"
    },
    {
      "fieldId": "line_7_capital_gain",
      "irsLineNumber": "7",
      "page": 1,
      "position": { "x": 504.0, "y": 90.0, "width": 72.0, "height": 12.0 },
      "format": "CURRENCY_ROUNDED",
      "alignment": "RIGHT",
      "dataPath": "$.income.capitalGain"
    },
    {
      "fieldId": "line_7_form_8949_required",
      "irsLineNumber": "7",
      "page": 1,
      "position": { "x": 485.0, "y": 90.0, "width": 8.0, "height": 8.0 },
      "format": "CHECKBOX_BOOLEAN",
      "alignment": "CENTER",
      "dataPath": "$.income.form8949Required"
    },
    {
      "fieldId": "line_7b_schedule_d_not_required_checkbox",
      "irsLineNumber": "7b",
      "page": 1,
      "position": { "x": 445.0, "y": 90.0, "width": 8.0, "height": 8.0 },
      "format": "CHECKBOX_BOOLEAN",
      "alignment": "CENTER",
      "dataPath": "$.income.scheduleDNotRequired"
    },
    {
      "fieldId": "line_7b_includes_child_capital_gain_checkbox",
      "irsLineNumber": "7b",
      "page": 1,
      "position": { "x": 480.0, "y": 90.0, "width": 8.0, "height": 8.0 },
      "format": "CHECKBOX_BOOLEAN",
      "alignment": "CENTER",
      "dataPath": "$.income.includesChildCapitalGainOrLoss"
    },
    {
      "fieldId": "line_8_schedule_1_income",
      "irsLineNumber": "8",
      "page": 1,
      "position": { "x": 504.0, "y": 78.0, "width": 72.0, "height": 12.0 },
      "format": "CURRENCY_ROUNDED",
      "alignment": "RIGHT",
      "dataPath": "$.income.schedule1Line10Income"
    },
    {
      "fieldId": "line_9_total_income",
      "irsLineNumber": "9",
      "page": 1,
      "position": { "x": 504.0, "y": 66.0, "width": 72.0, "height": 12.0 },
      "format": "CURRENCY_ROUNDED",
      "alignment": "RIGHT",
      "dataPath": "$.income.totalIncome"
    },
    {
      "fieldId": "line_10_adjustments_to_income",
      "irsLineNumber": "10",
      "page": 1,
      "position": { "x": 504.0, "y": 54.0, "width": 72.0, "height": 12.0 },
      "format": "CURRENCY_ROUNDED",
      "alignment": "RIGHT",
      "dataPath": "$.income.schedule1Line26Adjustments"
    },
    {
      "fieldId": "line_11a_agi",
      "irsLineNumber": "11a",
      "page": 1,
      "position": { "x": 504.0, "y": 42.0, "width": 72.0, "height": 12.0 },
      "format": "CURRENCY_ROUNDED",
      "alignment": "RIGHT",
      "dataPath": "$.tax.adjustedGrossIncome"
    },
    {
      "fieldId": "page2_taxpayer_name",
      "irsLineNumber": "Header",
      "page": 2,
      "position": { "x": 36.0, "y": 760.0, "width": 250.0, "height": 12.0 },
      "format": "UPPERCASE_TEXT",
      "alignment": "LEFT",
      "dataPath": "$.taxpayer.firstNameAndInitial"
    },
    {
      "fieldId": "page2_taxpayer_ssn",
      "irsLineNumber": "Header",
      "page": 2,
      "position": { "x": 469.0, "y": 760.0, "width": 107.0, "height": 12.0 },
      "format": "SSN_COMBED",
      "alignment": "LEFT",
      "dataPath": "$.taxpayer.ssn"
    },
    {
      "fieldId": "line_11b_agi_page2",
      "irsLineNumber": "11b",
      "page": 2,
      "position": { "x": 504.0, "y": 744.0, "width": 72.0, "height": 12.0 },
      "format": "CURRENCY_ROUNDED",
      "alignment": "RIGHT",
      "dataPath": "$.tax.adjustedGrossIncome"
    },
    {
      "fieldId": "std_deduction_taxpayer_dependent",
      "irsLineNumber": "12a",
      "page": 2,
      "position": { "x": 175.0, "y": 728.0, "width": 8.0, "height": 8.0 },
      "format": "CHECKBOX_BOOLEAN",
      "alignment": "CENTER",
      "dataPath": "$.standardDeduction.someoneCanClaimYouAsDependent"
    },
    {
      "fieldId": "std_deduction_spouse_dependent",
      "irsLineNumber": "12a",
      "page": 2,
      "position": { "x": 285.0, "y": 728.0, "width": 8.0, "height": 8.0 },
      "format": "CHECKBOX_BOOLEAN",
      "alignment": "CENTER",
      "dataPath": "$.standardDeduction.someoneCanClaimSpouseAsDependent"
    },
    {
      "fieldId": "std_deduction_spouse_itemizes",
      "irsLineNumber": "12b",
      "page": 2,
      "position": { "x": 215.0, "y": 716.0, "width": 8.0, "height": 8.0 },
      "format": "CHECKBOX_BOOLEAN",
      "alignment": "CENTER",
      "dataPath": "$.standardDeduction.spouseItemizesSeparateReturn"
    },
    {
      "fieldId": "std_deduction_dual_status_alien",
      "irsLineNumber": "12c",
      "page": 2,
      "position": { "x": 215.0, "y": 704.0, "width": 8.0, "height": 8.0 },
      "format": "CHECKBOX_BOOLEAN",
      "alignment": "CENTER",
      "dataPath": "$.standardDeduction.dualStatusAlien"
    },
    {
      "fieldId": "std_deduction_taxpayer_age",
      "irsLineNumber": "12d",
      "page": 2,
      "position": { "x": 180.0, "y": 694.0, "width": 8.0, "height": 8.0 },
      "format": "CHECKBOX_BOOLEAN",
      "alignment": "CENTER",
      "dataPath": "$.standardDeduction.taxpayerBornBefore1961"
    },
    {
      "fieldId": "std_deduction_taxpayer_blind",
      "irsLineNumber": "12d",
      "page": 2,
      "position": { "x": 255.0, "y": 694.0, "width": 8.0, "height": 8.0 },
      "format": "CHECKBOX_BOOLEAN",
      "alignment": "CENTER",
      "dataPath": "$.standardDeduction.taxpayerBlind"
    },
    {
      "fieldId": "std_deduction_spouse_age",
      "irsLineNumber": "12d",
      "page": 2,
      "position": { "x": 355.0, "y": 694.0, "width": 8.0, "height": 8.0 },
      "format": "CHECKBOX_BOOLEAN",
      "alignment": "CENTER",
      "dataPath": "$.standardDeduction.spouseBornBefore1961"
    },
    {
      "fieldId": "std_deduction_spouse_blind",
      "irsLineNumber": "12d",
      "page": 2,
      "position": { "x": 430.0, "y": 694.0, "width": 8.0, "height": 8.0 },
      "format": "CHECKBOX_BOOLEAN",
      "alignment": "CENTER",
      "dataPath": "$.standardDeduction.spouseBlind"
    },
    {
      "fieldId": "line_12_standard_or_itemized_deduction",
      "irsLineNumber": "12",
      "page": 2,
      "position": { "x": 504.0, "y": 684.0, "width": 72.0, "height": 12.0 },
      "format": "CURRENCY_ROUNDED",
      "alignment": "RIGHT",
      "dataPath": "$.tax.standardOrItemizedDeduction"
    },
    {
      "fieldId": "line_12e_standard_or_itemized_deduction",
      "irsLineNumber": "12e",
      "page": 2,
      "position": { "x": 504.0, "y": 684.0, "width": 72.0, "height": 12.0 },
      "format": "CURRENCY_ROUNDED",
      "alignment": "RIGHT",
      "dataPath": "$.tax.standardOrItemizedDeduction"
    },
    {
      "fieldId": "line_13a_qbi_deduction",
      "irsLineNumber": "13a",
      "page": 2,
      "position": { "x": 504.0, "y": 672.0, "width": 72.0, "height": 12.0 },
      "format": "CURRENCY_ROUNDED",
      "alignment": "RIGHT",
      "dataPath": "$.tax.qbiDeduction"
    },
    {
      "fieldId": "line_13b_additional_deductions",
      "irsLineNumber": "13b",
      "page": 2,
      "position": { "x": 504.0, "y": 660.0, "width": 72.0, "height": 12.0 },
      "format": "CURRENCY_ROUNDED",
      "alignment": "RIGHT",
      "dataPath": "$.tax.additionalDeductions"
    },
    {
      "fieldId": "line_14_total_deductions",
      "irsLineNumber": "14",
      "page": 2,
      "position": { "x": 504.0, "y": 648.0, "width": 72.0, "height": 12.0 },
      "format": "CURRENCY_ROUNDED",
      "alignment": "RIGHT",
      "dataPath": "$.tax.totalDeductions"
    },
    {
      "fieldId": "line_15_taxable_income",
      "irsLineNumber": "15",
      "page": 2,
      "position": { "x": 504.0, "y": 636.0, "width": 72.0, "height": 12.0 },
      "format": "CURRENCY_ROUNDED",
      "alignment": "RIGHT",
      "dataPath": "$.tax.taxableIncome"
    },
    {
      "fieldId": "line_16_tax",
      "irsLineNumber": "16",
      "page": 2,
      "position": { "x": 504.0, "y": 624.0, "width": 72.0, "height": 12.0 },
      "format": "CURRENCY_ROUNDED",
      "alignment": "RIGHT",
      "dataPath": "$.tax.tentativeTax"
    },
    {
      "fieldId": "line_16_form_8814_checkbox",
      "irsLineNumber": "16",
      "page": 2,
      "position": { "x": 380.0, "y": 624.0, "width": 8.0, "height": 8.0 },
      "format": "CHECKBOX_BOOLEAN",
      "alignment": "CENTER",
      "dataPath": "$.tax.form8814Checked"
    },
    {
      "fieldId": "line_16_form_4972_checkbox",
      "irsLineNumber": "16",
      "page": 2,
      "position": { "x": 420.0, "y": 624.0, "width": 8.0, "height": 8.0 },
      "format": "CHECKBOX_BOOLEAN",
      "alignment": "CENTER",
      "dataPath": "$.tax.form4972Checked"
    },
    {
      "fieldId": "line_16_other_form_checkbox",
      "irsLineNumber": "16",
      "page": 2,
      "position": { "x": 455.0, "y": 624.0, "width": 8.0, "height": 8.0 },
      "format": "CHECKBOX_BOOLEAN",
      "alignment": "CENTER",
      "dataPath": "$.tax.otherTaxFormChecked"
    },
    {
      "fieldId": "line_16_other_form_name",
      "irsLineNumber": "16",
      "page": 2,
      "position": { "x": 468.0, "y": 624.0, "width": 32.0, "height": 10.0 },
      "format": "UPPERCASE_TEXT",
      "alignment": "LEFT",
      "dataPath": "$.tax.otherTaxFormName"
    },
    {
      "fieldId": "line_17_schedule_2_line_3",
      "irsLineNumber": "17",
      "page": 2,
      "position": { "x": 504.0, "y": 612.0, "width": 72.0, "height": 12.0 },
      "format": "CURRENCY_ROUNDED",
      "alignment": "RIGHT",
      "dataPath": "$.tax.schedule2Line3"
    },
    {
      "fieldId": "line_18_total_tax_before_credits",
      "irsLineNumber": "18",
      "page": 2,
      "position": { "x": 504.0, "y": 600.0, "width": 72.0, "height": 12.0 },
      "format": "CURRENCY_ROUNDED",
      "alignment": "RIGHT",
      "dataPath": "$.tax.totalTaxBeforeCredits"
    },
    {
      "fieldId": "line_19_child_tax_credit",
      "irsLineNumber": "19",
      "page": 2,
      "position": { "x": 504.0, "y": 588.0, "width": 72.0, "height": 12.0 },
      "format": "CURRENCY_ROUNDED",
      "alignment": "RIGHT",
      "dataPath": "$.tax.childTaxCreditSchedule8812"
    },
    {
      "fieldId": "line_20_schedule_3_line_8",
      "irsLineNumber": "20",
      "page": 2,
      "position": { "x": 504.0, "y": 576.0, "width": 72.0, "height": 12.0 },
      "format": "CURRENCY_ROUNDED",
      "alignment": "RIGHT",
      "dataPath": "$.tax.schedule3Line8Credits"
    },
    {
      "fieldId": "line_21_total_credits",
      "irsLineNumber": "21",
      "page": 2,
      "position": { "x": 504.0, "y": 564.0, "width": 72.0, "height": 12.0 },
      "format": "CURRENCY_ROUNDED",
      "alignment": "RIGHT",
      "dataPath": "$.tax.totalCreditsLine21"
    },
    {
      "fieldId": "line_22_tax_after_credits",
      "irsLineNumber": "22",
      "page": 2,
      "position": { "x": 504.0, "y": 552.0, "width": 72.0, "height": 12.0 },
      "format": "CURRENCY_ROUNDED",
      "alignment": "RIGHT",
      "dataPath": "$.tax.taxAfterCreditsLine22"
    },
    {
      "fieldId": "line_23_other_taxes_schedule_2",
      "irsLineNumber": "23",
      "page": 2,
      "position": { "x": 504.0, "y": 540.0, "width": 72.0, "height": 12.0 },
      "format": "CURRENCY_ROUNDED",
      "alignment": "RIGHT",
      "dataPath": "$.tax.otherTaxesSchedule2Line21"
    },
    {
      "fieldId": "line_24_total_tax",
      "irsLineNumber": "24",
      "page": 2,
      "position": { "x": 504.0, "y": 528.0, "width": 72.0, "height": 12.0 },
      "format": "CURRENCY_ROUNDED",
      "alignment": "RIGHT",
      "dataPath": "$.tax.totalTax"
    },
    {
      "fieldId": "line_25a_w2_withholding",
      "irsLineNumber": "25a",
      "page": 2,
      "position": { "x": 410.4, "y": 504.0, "width": 71.25, "height": 12.0 },
      "format": "CURRENCY_ROUNDED",
      "alignment": "RIGHT",
      "dataPath": "$.payments.w2Withholding"
    },
    {
      "fieldId": "line_25b_1099_withholding",
      "irsLineNumber": "25b",
      "page": 2,
      "position": { "x": 410.4, "y": 492.0, "width": 71.25, "height": 12.0 },
      "format": "CURRENCY_ROUNDED",
      "alignment": "RIGHT",
      "dataPath": "$.payments.withholding1099"
    },
    {
      "fieldId": "line_25c_other_withholding",
      "irsLineNumber": "25c",
      "page": 2,
      "position": { "x": 410.4, "y": 480.0, "width": 71.25, "height": 12.0 },
      "format": "CURRENCY_ROUNDED",
      "alignment": "RIGHT",
      "dataPath": "$.payments.withholdingOtherForms"
    },
    {
      "fieldId": "line_25d_total_withholding",
      "irsLineNumber": "25d",
      "page": 2,
      "position": { "x": 504.0, "y": 468.0, "width": 72.0, "height": 12.0 },
      "format": "CURRENCY_ROUNDED",
      "alignment": "RIGHT",
      "dataPath": "$.payments.totalWithholding"
    },
    {
      "fieldId": "line_26_estimated_tax_payments",
      "irsLineNumber": "26",
      "page": 2,
      "position": { "x": 504.0, "y": 432.0, "width": 72.0, "height": 12.0 },
      "format": "CURRENCY_ROUNDED",
      "alignment": "RIGHT",
      "dataPath": "$.payments.estimatedTaxPayments"
    },
    {
      "fieldId": "line_26_former_spouse_ssn",
      "irsLineNumber": "26",
      "page": 2,
      "position": { "x": 236.0, "y": 432.0, "width": 75.0, "height": 12.0 },
      "format": "SSN_MASKED",
      "alignment": "LEFT",
      "dataPath": "$.payments.formerSpouseSsn"
    },
    {
      "fieldId": "line_27_earned_income_credit",
      "irsLineNumber": "27",
      "page": 2,
      "position": { "x": 504.0, "y": 420.0, "width": 72.0, "height": 12.0 },
      "format": "CURRENCY_ROUNDED",
      "alignment": "RIGHT",
      "dataPath": "$.payments.earnedIncomeCreditEIC"
    },
    {
      "fieldId": "line_27b_clergy_schedule_se",
      "irsLineNumber": "27b",
      "page": 2,
      "position": { "x": 485.0, "y": 420.0, "width": 8.0, "height": 8.0 },
      "format": "CHECKBOX_BOOLEAN",
      "alignment": "CENTER",
      "dataPath": "$.payments.clergyFilingScheduleSe"
    },
    {
      "fieldId": "line_27c_elect_not_to_claim_eic",
      "irsLineNumber": "27c",
      "page": 2,
      "position": { "x": 460.0, "y": 420.0, "width": 8.0, "height": 8.0 },
      "format": "CHECKBOX_BOOLEAN",
      "alignment": "CENTER",
      "dataPath": "$.payments.electNotToClaimEic"
    },
    {
      "fieldId": "line_28_additional_child_tax_credit",
      "irsLineNumber": "28",
      "page": 2,
      "position": { "x": 504.0, "y": 408.0, "width": 72.0, "height": 12.0 },
      "format": "CURRENCY_ROUNDED",
      "alignment": "RIGHT",
      "dataPath": "$.payments.additionalChildTaxCredit"
    },
    {
      "fieldId": "line_28_elect_not_to_claim_actc",
      "irsLineNumber": "28",
      "page": 2,
      "position": { "x": 485.0, "y": 408.0, "width": 8.0, "height": 8.0 },
      "format": "CHECKBOX_BOOLEAN",
      "alignment": "CENTER",
      "dataPath": "$.payments.electNotToClaimActc"
    },
    {
      "fieldId": "line_29_american_opportunity_credit",
      "irsLineNumber": "29",
      "page": 2,
      "position": { "x": 504.0, "y": 396.0, "width": 72.0, "height": 12.0 },
      "format": "CURRENCY_ROUNDED",
      "alignment": "RIGHT",
      "dataPath": "$.payments.americanOpportunityCredit"
    },
    {
      "fieldId": "line_30_refundable_adoption_credit",
      "irsLineNumber": "30",
      "page": 2,
      "position": { "x": 504.0, "y": 384.0, "width": 72.0, "height": 12.0 },
      "format": "CURRENCY_ROUNDED",
      "alignment": "RIGHT",
      "dataPath": "$.payments.refundableAdoptionCredit"
    },
    {
      "fieldId": "line_31_schedule_3_line_15",
      "irsLineNumber": "31",
      "page": 2,
      "position": { "x": 504.0, "y": 372.0, "width": 72.0, "height": 12.0 },
      "format": "CURRENCY_ROUNDED",
      "alignment": "RIGHT",
      "dataPath": "$.payments.schedule3Line15RefundableCredits"
    },
    {
      "fieldId": "line_32_total_other_payments",
      "irsLineNumber": "32",
      "page": 2,
      "position": { "x": 504.0, "y": 348.0, "width": 72.0, "height": 12.0 },
      "format": "CURRENCY_ROUNDED",
      "alignment": "RIGHT",
      "dataPath": "$.payments.totalOtherPayments"
    },
    {
      "fieldId": "line_33_total_payments",
      "irsLineNumber": "33",
      "page": 2,
      "position": { "x": 504.0, "y": 324.0, "width": 72.0, "height": 12.0 },
      "format": "CURRENCY_ROUNDED",
      "alignment": "RIGHT",
      "dataPath": "$.payments.totalPayments"
    },
    {
      "fieldId": "line_34_overpayment",
      "irsLineNumber": "34",
      "page": 2,
      "position": { "x": 504.0, "y": 312.0, "width": 72.0, "height": 12.0 },
      "format": "CURRENCY_ROUNDED",
      "alignment": "RIGHT",
      "dataPath": "$.refund.overpaidAmount"
    },
    {
      "fieldId": "line_35a_refund_amount",
      "irsLineNumber": "35a",
      "page": 2,
      "position": { "x": 504.0, "y": 300.0, "width": 72.0, "height": 12.0 },
      "format": "CURRENCY_ROUNDED",
      "alignment": "RIGHT",
      "dataPath": "$.refund.overpaidAmount"
    },
    {
      "fieldId": "line_35a_form_8888_attached",
      "irsLineNumber": "35a",
      "page": 2,
      "position": { "x": 380.0, "y": 300.0, "width": 8.0, "height": 8.0 },
      "format": "CHECKBOX_BOOLEAN",
      "alignment": "CENTER",
      "dataPath": "$.refund.form8888Attached"
    },
    {
      "fieldId": "line_35b_routing_number",
      "irsLineNumber": "35b",
      "page": 2,
      "position": { "x": 127.6, "y": 286.0, "width": 110.0, "height": 12.0 },
      "format": "UPPERCASE_TEXT",
      "alignment": "LEFT",
      "dataPath": "$.refund.routingNumber"
    },
    {
      "fieldId": "line_35c_type_checking",
      "irsLineNumber": "35c",
      "page": 2,
      "position": { "x": 295.0, "y": 286.0, "width": 8.0, "height": 8.0 },
      "format": "CHECKBOX_BOOLEAN",
      "alignment": "CENTER",
      "dataPath": "$.refund.isCheckingAccount"
    },
    {
      "fieldId": "line_35c_type_savings",
      "irsLineNumber": "35c",
      "page": 2,
      "position": { "x": 345.0, "y": 286.0, "width": 8.0, "height": 8.0 },
      "format": "CHECKBOX_BOOLEAN",
      "alignment": "CENTER",
      "dataPath": "$.refund.isSavingsAccount"
    },
    {
      "fieldId": "line_35d_account_number",
      "irsLineNumber": "35d",
      "page": 2,
      "position": { "x": 410.0, "y": 286.0, "width": 160.0, "height": 12.0 },
      "format": "UPPERCASE_TEXT",
      "alignment": "LEFT",
      "dataPath": "$.refund.accountNumber"
    },
    {
      "fieldId": "line_36_applied_to_estimated_tax",
      "irsLineNumber": "36",
      "page": 2,
      "position": { "x": 504.0, "y": 270.0, "width": 72.0, "height": 12.0 },
      "format": "CURRENCY_ROUNDED",
      "alignment": "RIGHT",
      "dataPath": "$.refund.appliedToNextYearEstimatedTax"
    },
    {
      "fieldId": "line_37_amount_you_owe",
      "irsLineNumber": "37",
      "page": 2,
      "position": { "x": 504.0, "y": 228.0, "width": 72.0, "height": 12.0 },
      "format": "CURRENCY_ROUNDED",
      "alignment": "RIGHT",
      "dataPath": "$.balance.amountYouOwe"
    },
    {
      "fieldId": "line_38_estimated_tax_penalty",
      "irsLineNumber": "38",
      "page": 2,
      "position": { "x": 504.0, "y": 204.0, "width": 72.0, "height": 12.0 },
      "format": "CURRENCY_ROUNDED",
      "alignment": "RIGHT",
      "dataPath": "$.balance.estimatedTaxPenalty"
    },
    {
      "fieldId": "taxpayer_occupation",
      "irsLineNumber": "Sign Here",
      "page": 2,
      "position": { "x": 325.0, "y": 126.0, "width": 135.05, "height": 16.0 },
      "format": "UPPERCASE_TEXT",
      "alignment": "LEFT",
      "dataPath": "$.taxpayer.occupation"
    },
    {
      "fieldId": "taxpayer_pin",
      "irsLineNumber": "Sign Here",
      "page": 2,
      "position": { "x": 504.0, "y": 126.0, "width": 72.0, "height": 12.0 },
      "format": "UPPERCASE_TEXT",
      "alignment": "LEFT",
      "dataPath": "$.taxpayer.identityProtectionPin"
    },
    {
      "fieldId": "taxpayer_sign_date",
      "irsLineNumber": "Sign Here",
      "page": 2,
      "position": { "x": 278.0, "y": 126.0, "width": 45.0, "height": 16.0 },
      "format": "DATE_MMDDYYYY",
      "alignment": "LEFT",
      "dataPath": "$.taxpayer.signatureDate"
    },
    {
      "fieldId": "spouse_occupation",
      "irsLineNumber": "Sign Here",
      "page": 2,
      "position": { "x": 325.0, "y": 96.0, "width": 135.05, "height": 16.0 },
      "format": "UPPERCASE_TEXT",
      "alignment": "LEFT",
      "dataPath": "$.spouse.occupation"
    },
    {
      "fieldId": "spouse_pin",
      "irsLineNumber": "Sign Here",
      "page": 2,
      "position": { "x": 504.0, "y": 96.0, "width": 72.0, "height": 12.0 },
      "format": "UPPERCASE_TEXT",
      "alignment": "LEFT",
      "dataPath": "$.spouse.identityProtectionPin"
    },
    {
      "fieldId": "spouse_sign_date",
      "irsLineNumber": "Sign Here",
      "page": 2,
      "position": { "x": 278.0, "y": 96.0, "width": 45.0, "height": 16.0 },
      "format": "DATE_MMDDYYYY",
      "alignment": "LEFT",
      "dataPath": "$.spouse.signatureDate"
    },
    {
      "fieldId": "daytime_phone",
      "irsLineNumber": "Sign Here",
      "page": 2,
      "position": { "x": 127.6, "y": 84.0, "width": 145.25, "height": 12.0 },
      "format": "UPPERCASE_TEXT",
      "alignment": "LEFT",
      "dataPath": "$.taxpayer.phone"
    },
    {
      "fieldId": "taxpayer_email",
      "irsLineNumber": "Sign Here",
      "page": 2,
      "position": { "x": 325.0, "y": 84.0, "width": 245.0, "height": 12.0 },
      "format": "UPPERCASE_TEXT",
      "alignment": "LEFT",
      "dataPath": "$.taxpayer.email"
    },
    {
      "fieldId": "presidential_election_you",
      "irsLineNumber": "Header",
      "page": 1,
      "position": { "x": 532.0, "y": 636.0, "width": 8.0, "height": 8.0 },
      "format": "CHECKBOX_BOOLEAN",
      "alignment": "CENTER",
      "dataPath": "$.header.presidentialElectionYou"
    },
    {
      "fieldId": "presidential_election_spouse",
      "irsLineNumber": "Header",
      "page": 1,
      "position": { "x": 568.0, "y": 636.0, "width": 8.0, "height": 8.0 },
      "format": "CHECKBOX_BOOLEAN",
      "alignment": "CENTER",
      "dataPath": "$.header.presidentialElectionSpouse"
    }
  ]
}
```

---

## 8. Deeply Nested Data Resolution via JSONPath

### 8.1 The Binding Mechanism
The specification uses standard RFC 9535 JSONPath expressions within `dataPath` to access values in arbitrary hierarchical datasets. This decouples the visual annotation from specific domain schemas. Applications must configure their JSONPath reader with `Option.DEFAULT_PATH_LEAF_TO_NULL` and `Option.SUPPRESS_EXCEPTIONS` to evaluate non-existent intermediate paths gracefully to empty strings.

### 8.2 Path Resolution Matrix

| Target Field | `dataPath` Syntax | Extracted Raw Value | Resolved Type |
| :--- | :--- | :--- | :--- |
| First Name | `$.taxpayer.firstNameAndInitial` | `"ROHIN"` | String |
| Deeply Nested City | `$.taxpayer.address.city` | `"AUSTIN"` | String |
| Dependent 0 SSN | `$.dependents[0].ssn` | `"987-65-4321"` | String (Array Index) |
| W-2 Wages | `$.income.w2Wages` | `115000.50` | Double / Number |
| Checkbox State | `$.filingStatus.isSingle` | `true` | Boolean |

---

## 9. Data Formatting Engine (`FormatType`)

### 9.1 Formatting Transformation Rules

| FormatType | Raw Input Sample | Rendered Output | Algorithmic Transformation |
| :--- | :--- | :--- | :--- |
| `UPPERCASE_TEXT` | `"Jane de Doe"` | `"JANE DE DOE"` | `val.toString().toUpperCase().trim()` |
| `CURRENCY_ROUNDED` | `115000.50` | `"115001"` | Strip non-numeric characters; round to nearest whole dollar |
| `CURRENCY_EXACT` | `4320.75` | `"4320.75"` | `String.format("%.2f", val)` (2 decimal places) |
| `CHECKBOX_BOOLEAN` | `true` or `"yes"` | `"\u2713"` (tick) | Map boolean truthy to ZapfDingbats byte `0x34` (`4`) |
| `SSN_COMBED` | `"123-45-6789"` | `Distributed` | Strip hyphens, segment into 3 parts, distribute across comb offsets |
| `SSN_MASKED` | `"999-88-7766"` | `"XXXXX7766"` | Strip non-digits, mask first 5 digits with `X` |
| `SSN_UNMASKED` | `"123-45-6789"` | `"123456789"` | Strip non-digits, render continuous string |
| `DATE_MMDDYYYY` | `"2026-04-15"` | `"04/15/2026"` | Parse ISO `YYYY-MM-DD` or slash dates, normalize to `MM/DD/YYYY` |

---

## 10. Future Work

* **Agentic OCR Confidence Scoring:** When a user uploads a W-2, our Vision-Language Models will assign a confidence score. If extraction confidence drops below 95%, we halt the automated flow to prevent bad data from reaching the PDF.
* **Human-In-The-Loop (HITL):** Fallback queues for edge cases (e.g., messy handwriting). A human operator will verify the flagged document, correct the extraction, and authorize the payload.

