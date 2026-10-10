package com.taxapp.validation;

import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Validates dynamic taxpayer JSON payloads against IRS formatting standards and integrity constraints.
 */
public class TaxDataValidator {

    private static final Pattern SSN_DIGITS_PATTERN = Pattern.compile("^\\d{9}$");
    private static final Pattern ROUTING_DIGITS_PATTERN = Pattern.compile("^\\d{9}$");
    private static final Pattern ISO_DATE_PATTERN = Pattern.compile("^(\\d{4})-(\\d{2})-(\\d{2})$");
    private static final Pattern US_DATE_PATTERN = Pattern.compile("^(\\d{2})/(\\d{2})/(\\d{4})$");

    private final Configuration jsonPathConfig;

    public TaxDataValidator() {
        this.jsonPathConfig = Configuration.defaultConfiguration()
                .addOptions(Option.DEFAULT_PATH_LEAF_TO_NULL, Option.SUPPRESS_EXCEPTIONS);
    }

    /**
     * Validates a raw JSON payload string and returns a ValidationResult.
     */
    public ValidationResult validate(String payerJson) {
        ValidationResult result = new ValidationResult();
        if (payerJson == null || payerJson.trim().isEmpty()) {
            result.addError("root", "ERR_PAYLOAD_EMPTY", "Taxpayer payload is null or empty");
            return result;
        }

        DocumentContext ctx;
        try {
            ctx = JsonPath.using(jsonPathConfig).parse(payerJson);
        } catch (Exception e) {
            result.addError("root", "ERR_JSON_SYNTAX", "Failed to parse taxpayer JSON: " + e.getMessage());
            return result;
        }

        validateTaxpayerIdentification(ctx, result);
        validateDependents(ctx, result);
        validateFilingStatus(ctx, result);
        validateBankingDetails(ctx, result);
        validateDates(ctx, result);
        validateNumericFields(ctx, result);

        return result;
    }

    /**
     * Validates Primary and Spouse identification.
     */
    private void validateTaxpayerIdentification(DocumentContext ctx, ValidationResult result) {
        String ssn = readString(ctx, "$.taxpayer.ssn");
        if (ssn == null || ssn.trim().isEmpty()) {
            result.addError("$.taxpayer.ssn", "ERR_SSN_MISSING", "Primary taxpayer SSN is required");
        } else {
            validateSsnFormat("$.taxpayer.ssn", ssn, result);
        }

        String spouseSsn = readString(ctx, "$.spouse.ssn");
        if (spouseSsn != null && !spouseSsn.trim().isEmpty()) {
            validateSsnFormat("$.spouse.ssn", spouseSsn, result);
        }
    }

    /**
     * Validates Dependent SSNs and relationship data.
     */
    private void validateDependents(DocumentContext ctx, ValidationResult result) {
        Object depsObj = ctx.read("$.dependents");
        if (depsObj instanceof List<?> depsList) {
            for (int i = 0; i < depsList.size(); i++) {
                String path = "$.dependents[" + i + "].ssn";
                String depSsn = readString(ctx, path);
                if (depSsn != null && !depSsn.trim().isEmpty()) {
                    validateSsnFormat(path, depSsn, result);
                } else {
                    result.addWarning(path, "WARN_DEP_SSN_MISSING", "Dependent at index " + i + " is missing an SSN");
                }
            }
        }
    }

    /**
     * Enforces IRS SSN rules: 9 numeric digits, no area 000/666/900-999, no group 00, no serial 0000.
     */
    public boolean validateSsnFormat(String fieldPath, String rawSsn, ValidationResult result) {
        if (rawSsn == null) {
            result.addError(fieldPath, "ERR_SSN_NULL", "SSN cannot be null");
            return false;
        }

        String clean = rawSsn.replaceAll("[^0-9]", "");
        if (!SSN_DIGITS_PATTERN.matcher(clean).matches()) {
            result.addError(fieldPath, "ERR_SSN_LENGTH", "SSN must contain exactly 9 numeric digits (found: " + clean.length() + ")");
            return false;
        }

        int area = Integer.parseInt(clean.substring(0, 3));
        int group = Integer.parseInt(clean.substring(3, 5));
        int serial = Integer.parseInt(clean.substring(5, 9));

        if (area == 0 || area == 666 || area >= 900) {
            result.addError(fieldPath, "ERR_SSN_INVALID_AREA", "SSN area number cannot be 000, 666, or 900-999 (found: " + clean.substring(0, 3) + ")");
            return false;
        }

        if (group == 0) {
            result.addError(fieldPath, "ERR_SSN_INVALID_GROUP", "SSN group number cannot be 00");
            return false;
        }

        if (serial == 0) {
            result.addError(fieldPath, "ERR_SSN_INVALID_SERIAL", "SSN serial number cannot be 0000");
            return false;
        }

        return true;
    }

    /**
     * Validates direct deposit routing numbers using the Federal Reserve ABA 9-digit checksum.
     */
    private void validateBankingDetails(DocumentContext ctx, ValidationResult result) {
        String routing = readString(ctx, "$.refund.routingNumber");
        if (routing != null && !routing.trim().isEmpty()) {
            validateRoutingNumber("$.refund.routingNumber", routing, result);
        }

        Boolean checking = readBoolean(ctx, "$.refund.isCheckingAccount");
        Boolean savings = readBoolean(ctx, "$.refund.isSavingsAccount");
        if (Boolean.TRUE.equals(checking) && Boolean.TRUE.equals(savings)) {
            result.addError("$.refund", "ERR_ACCOUNT_TYPE_CONFLICT", "Cannot select both checking and savings account for direct deposit");
        }
    }

    /**
     * Official ABA Routing Transit Number Checksum:
     * (3*(d1 + d4 + d7) + 7*(d2 + d5 + d8) + 1*(d3 + d6 + d9)) mod 10 == 0
     */
    public boolean validateRoutingNumber(String fieldPath, String rawRouting, ValidationResult result) {
        String clean = rawRouting.replaceAll("[^0-9]", "");
        if (!ROUTING_DIGITS_PATTERN.matcher(clean).matches()) {
            result.addError(fieldPath, "ERR_ROUTING_LENGTH", "ABA Routing number must be exactly 9 numeric digits");
            return false;
        }

        int[] d = new int[9];
        for (int i = 0; i < 9; i++) {
            d[i] = Character.getNumericValue(clean.charAt(i));
        }

        int checksum = (3 * (d[0] + d[3] + d[6]) + 7 * (d[1] + d[4] + d[7]) + 1 * (d[2] + d[5] + d[8])) % 10;
        if (checksum != 0) {
            result.addError(fieldPath, "ERR_ROUTING_CHECKSUM", "Invalid ABA routing transit checksum for: " + clean);
            return false;
        }

        return true;
    }

    /**
     * Verifies that exactly one primary filing status is selected.
     */
    private void validateFilingStatus(DocumentContext ctx, ValidationResult result) {
        int selectedCount = 0;
        String[] statuses = {
                "$.filingStatus.single",
                "$.filingStatus.marriedFilingJointly",
                "$.filingStatus.marriedFilingSeparately",
                "$.filingStatus.headOfHousehold",
                "$.filingStatus.qualifyingSurvivingSpouse"
        };

        for (String statusPath : statuses) {
            Boolean val = readBoolean(ctx, statusPath);
            if (Boolean.TRUE.equals(val)) {
                selectedCount++;
            }
        }

        if (selectedCount == 0) {
            result.addWarning("$.filingStatus", "WARN_NO_FILING_STATUS", "No filing status selected on Form 1040");
        } else if (selectedCount > 1) {
            result.addError("$.filingStatus", "ERR_MULTIPLE_FILING_STATUSES", "More than one filing status selected (" + selectedCount + " selected)");
        }
    }

    /**
     * Validates dates for correct format and logical range.
     */
    private void validateDates(DocumentContext ctx, ValidationResult result) {
        String[] datePaths = {
                "$.taxpayer.signatureDate",
                "$.spouse.signatureDate",
                "$.paidPreparer.signatureDate"
        };

        for (String path : datePaths) {
            String dateVal = readString(ctx, path);
            if (dateVal != null && !dateVal.trim().isEmpty()) {
                validateDateFormat(path, dateVal, result);
            }
        }
    }

    public boolean validateDateFormat(String fieldPath, String dateStr, ValidationResult result) {
        var isoMatcher = ISO_DATE_PATTERN.matcher(dateStr.trim());
        var usMatcher = US_DATE_PATTERN.matcher(dateStr.trim());

        int year, month, day;
        if (isoMatcher.matches()) {
            year = Integer.parseInt(isoMatcher.group(1));
            month = Integer.parseInt(isoMatcher.group(2));
            day = Integer.parseInt(isoMatcher.group(3));
        } else if (usMatcher.matches()) {
            month = Integer.parseInt(usMatcher.group(1));
            day = Integer.parseInt(usMatcher.group(2));
            year = Integer.parseInt(usMatcher.group(3));
        } else {
            result.addError(fieldPath, "ERR_DATE_FORMAT", "Date must be YYYY-MM-DD or MM/DD/YYYY: " + dateStr);
            return false;
        }

        if (month < 1 || month > 12) {
            result.addError(fieldPath, "ERR_DATE_MONTH", "Invalid month in date: " + month);
            return false;
        }

        if (day < 1 || day > 31) {
            result.addError(fieldPath, "ERR_DATE_DAY", "Invalid day in date: " + day);
            return false;
        }

        if (year < 1900 || year > 2100) {
            result.addWarning(fieldPath, "WARN_DATE_YEAR", "Unusual year in date: " + year);
        }

        return true;
    }

    private void validateNumericFields(DocumentContext ctx, ValidationResult result) {
        String[] currencyPaths = {
                "$.income.w2Wages",
                "$.income.taxableInterest",
                "$.income.totalIncome",
                "$.taxAndCredits.totalTax",
                "$.payments.totalPayments"
        };

        for (String path : currencyPaths) {
            Object val = ctx.read(path);
            if (val instanceof Number num) {
                if (Double.isNaN(num.doubleValue()) || Double.isInfinite(num.doubleValue())) {
                    result.addError(path, "ERR_CURRENCY_INVALID", "Currency value cannot be NaN or Infinite");
                }
            }
        }
    }

    private String readString(DocumentContext ctx, String path) {
        try {
            Object val = ctx.read(path);
            return val != null ? val.toString() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private Boolean readBoolean(DocumentContext ctx, String path) {
        try {
            Object val = ctx.read(path);
            if (val instanceof Boolean b) {
                return b;
            }
            if (val instanceof String s) {
                return "true".equalsIgnoreCase(s) || "yes".equalsIgnoreCase(s);
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }
}
