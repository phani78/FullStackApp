package com.bank.origination.security;

import java.util.regex.Pattern;

public final class PiiMasker {
    private static final Pattern SSN = Pattern.compile("(?<!\\d)\\d{3}-?\\d{2}-?\\d{4}(?!\\d)");
    private static final Pattern JWT = Pattern.compile("(?i)bearer\\s+[a-z0-9._-]+");
    private static final Pattern ISO_DATE = Pattern.compile("\\b\\d{4}-\\d{2}-\\d{2}\\b");
    private static final Pattern US_DATE = Pattern.compile("\\b\\d{1,2}/\\d{1,2}/\\d{4}\\b");

    private PiiMasker() {}

    public static String mask(String value) {
        if (value == null) {
            return null;
        }
        String masked = SSN.matcher(value).replaceAll("***-**-****");
        masked = ISO_DATE.matcher(masked).replaceAll("[DATE REDACTED]");
        masked = US_DATE.matcher(masked).replaceAll("[DATE REDACTED]");
        return JWT.matcher(masked).replaceAll("Bearer [REDACTED]");
    }

    public static String maskSsn(String ssn) {
        if (ssn == null || ssn.length() < 4) {
            return "****";
        }
        return "****" + ssn.substring(ssn.length() - 4);
    }
}
