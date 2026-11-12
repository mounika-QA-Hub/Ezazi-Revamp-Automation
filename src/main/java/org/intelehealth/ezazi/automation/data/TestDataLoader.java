package org.intelehealth.ezazi.automation.data;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.intelehealth.ezazi.automation.config.ConfigKeys;
import org.intelehealth.ezazi.automation.config.ConfigReader;
import org.intelehealth.ezazi.automation.exceptions.FrameworkException;
import org.intelehealth.ezazi.automation.utils.ExcelUtils;
import org.intelehealth.ezazi.automation.utils.JsonUtils;

/**
 * Loads, filters (Run = N) and converts data rows for a test method annotated with
 * {@link TestDataSource}. Keeps DataProvider classes one-liners.
 */
public final class TestDataLoader {

    private static final Logger LOG = LogManager.getLogger(TestDataLoader.class);

    private TestDataLoader() {
    }

    /**
     * @param testMethod method carrying {@link TestDataSource}
     * @return TestNG DataProvider array, one row per runnable test case
     */
    public static Object[][] forMethod(Method testMethod) {
        TestDataSource source = testMethod.getAnnotation(TestDataSource.class);
        if (source == null) {
            throw new FrameworkException("Method " + testMethod.getDeclaringClass().getSimpleName() + "." + testMethod.getName()
                    + " uses the generic DataProvider but has no @TestDataSource annotation.");
        }
        List<Object> rows = load(source.file(), source.sheet(), source.type());
        if (rows.isEmpty()) {
            LOG.warn("No runnable rows for {} - the test will not execute", testMethod.getName());
        }
        Object[][] result = new Object[rows.size()][1];
        for (int i = 0; i < rows.size(); i++) {
            result[i][0] = rows.get(i);
        }
        return result;
    }

    /**
     * Loads runnable rows from Excel or JSON.
     *
     * @param file  path (blank = default workbook)
     * @param sheet Excel sheet
     * @param type  {@link TestData} or a POJO type
     * @return converted rows
     */
    public static List<Object> load(String file, String sheet, Class<?> type) {
        String location = (file == null || file.isBlank()) ? ConfigReader.get(ConfigKeys.TESTDATA_EXCEL_FILE) : file;
        String lower = location.toLowerCase(Locale.ROOT);
        List<Map<String, String>> raw;
        if (lower.endsWith(".json")) {
            raw = JsonUtils.readRows(location);
        } else if (lower.endsWith(".xlsx") || lower.endsWith(".xls")) {
            if (sheet == null || sheet.isBlank()) {
                throw new FrameworkException("@TestDataSource(sheet = ...) is required for Excel file " + location);
            }
            raw = ExcelUtils.readSheet(location, sheet);
        } else {
            throw new FrameworkException("Unsupported test data file type: " + location + " (use .xlsx, .xls or .json)");
        }

        List<Object> rows = new ArrayList<>();
        for (Map<String, String> row : raw) {
            String id = row.getOrDefault(TestData.TEST_CASE_ID, "?");
            if (!TestData.isRunnable(row)) {
                LOG.info("Skipping {} (Run = N) from {}", id, location);
                continue;
            }
            TestData data = new TestData(row);
            rows.add(type == TestData.class ? data : data.as(type));
        }
        LOG.info("Loaded {} runnable row(s) from {}{}", rows.size(), location, sheet == null || sheet.isBlank() ? "" : "#" + sheet);
        return rows;
    }
}
