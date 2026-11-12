package org.intelehealth.ezazi.automation.data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.intelehealth.ezazi.automation.config.ConfigReader;
import org.intelehealth.ezazi.automation.exceptions.FrameworkException;
import org.intelehealth.ezazi.automation.utils.DateTimeUtils;
import org.intelehealth.ezazi.automation.utils.JsonUtils;

/**
 * One row of test data (Excel or JSON) with typed, null-safe accessors.
 * <p>
 * <b>Placeholders:</b> a cell like {@code ${EZAZI_USERNAME}} is replaced at read time with the secret
 * (env variable / .env) or configuration value of that name - so credentials never live in data files.
 * <p>
 * <b>Typos fail fast:</b> {@link #get(String)} throws if the column does not exist, instead of
 * silently returning an empty string.
 */
public final class TestData implements TestCaseInfo {

    public static final String TEST_CASE_ID = "TestCaseID";
    public static final String DESCRIPTION = "Description";
    public static final String EXPECTED_RESULT = "ExpectedResult";
    public static final String RUN = "Run";

    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([^}]+)}");

    private final Map<String, String> values;

    /**
     * @param row raw column -> value map
     */
    public TestData(Map<String, String> row) {
        this.values = Collections.unmodifiableMap(new LinkedHashMap<>(row));
    }

    /**
     * @param row raw row
     * @return true unless the Run column says N / No / false
     */
    public static boolean isRunnable(Map<String, String> row) {
        String run = row.getOrDefault(RUN, "Y").trim().toUpperCase(Locale.ROOT);
        return run.isEmpty() || run.equals("Y") || run.equals("YES") || run.equals("TRUE");
    }

    @Override
    public String testCaseId() {
        return values.getOrDefault(TEST_CASE_ID, "");
    }

    @Override
    public String description() {
        return values.getOrDefault(DESCRIPTION, "");
    }

    /** @return content of the ExpectedResult column */
    public String expectedResult() {
        return get(EXPECTED_RESULT);
    }

    /**
     * @param column column name (exact header text)
     * @return value with placeholders resolved; "" for an empty cell
     * @throws FrameworkException if the column does not exist
     */
    public String get(String column) {
        if (!values.containsKey(column)) {
            throw new FrameworkException("Column '" + column + "' not found for " + testCaseId() + ". Available: " + values.keySet());
        }
        return resolvePlaceholders(values.get(column));
    }

    /** @return value or default when the column is missing/blank */
    public String getOrDefault(String column, String defaultValue) {
        String value = values.containsKey(column) ? resolvePlaceholders(values.get(column)) : "";
        return value.isBlank() ? defaultValue : value;
    }

    /** @return Optional value (empty if missing or blank) */
    public Optional<String> find(String column) {
        return Optional.ofNullable(getOrDefault(column, null));
    }

    /** @return true if the column exists and is not blank */
    public boolean has(String column) {
        return find(column).isPresent();
    }

    /** @return integer value */
    public int getInt(String column) {
        String value = get(column);
        try {
            return (int) Double.parseDouble(value);
        } catch (NumberFormatException e) {
            throw new FrameworkException("Column '" + column + "' of " + testCaseId() + " is not a number: '" + value + "'", e);
        }
    }

    /** @return true for Y/YES/TRUE/1 */
    public boolean getBoolean(String column) {
        String v = get(column).trim().toUpperCase(Locale.ROOT);
        return v.equals("Y") || v.equals("YES") || v.equals("TRUE") || v.equals("1");
    }

    /** @return date (supports tokens like TODAY-1), or null if blank */
    public LocalDate getDate(String column) {
        return DateTimeUtils.resolveDate(get(column));
    }

    /** @return time (supports NOW+30m, 23:30, 11:30 PM), or null if blank */
    public LocalTime getTime(String column) {
        return DateTimeUtils.resolveTime(get(column));
    }

    /** @return date-time (supports NOW-2h, TODAY-1 23:30), or null if blank */
    public LocalDateTime getDateTime(String column) {
        return DateTimeUtils.resolveDateTime(get(column));
    }

    /** @return the raw, unresolved row */
    public Map<String, String> asMap() {
        return values;
    }

    /**
     * Maps the row onto a POJO (field names = column names).
     *
     * @param type POJO class
     * @param <T>  type
     * @return POJO instance
     */
    public <T> T as(Class<T> type) {
        Map<String, String> resolved = new LinkedHashMap<>();
        values.forEach((k, v) -> resolved.put(k, resolvePlaceholders(v)));
        return JsonUtils.convert(resolved, type);
    }

    private static String resolvePlaceholders(String value) {
        if (value == null || !value.contains("${")) {
            return value == null ? "" : value;
        }
        Matcher m = PLACEHOLDER.matcher(value);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String name = m.group(1).trim();
            String replacement = ConfigReader.findSecret(name)
                    .or(() -> ConfigReader.getOptional(name))
                    .orElseThrow(() -> new FrameworkException("Test data placeholder ${" + name
                            + "} is not defined. Set it in .env / environment variables or in config properties."));
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    @Override
    public String toString() {
        return testCaseId() + " - " + description();
    }
}
