package org.intelehealth.ezazi.automation.utils;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.intelehealth.ezazi.automation.exceptions.FrameworkException;

/**
 * Reads Excel sheets into rows of {@code column -> value} (row 1 = header).
 * <p>
 * Every cell is returned as the text the user sees in Excel (via {@link DataFormatter}), so
 * "007" stays "007" and formulas are evaluated. Parsed sheets are cached, which makes parallel
 * DataProviders cheap and thread-safe (the cache holds immutable copies).
 */
public final class ExcelUtils {

    private static final Logger LOG = LogManager.getLogger(ExcelUtils.class);
    private static final Map<String, List<Map<String, String>>> CACHE = new ConcurrentHashMap<>();

    private ExcelUtils() {
    }

    /**
     * Reads all non-empty rows of a sheet.
     *
     * @param location  file path or classpath resource (.xlsx / .xls)
     * @param sheetName sheet (tab) name, case-sensitive
     * @return immutable list of immutable, ordered rows
     */
    public static List<Map<String, String>> readSheet(String location, String sheetName) {
        return CACHE.computeIfAbsent(location + "#" + sheetName, key -> load(location, sheetName));
    }

    private static List<Map<String, String>> load(String location, String sheetName) {
        try (InputStream in = ResourceUtils.open(location); Workbook workbook = WorkbookFactory.create(in)) {
            Sheet sheet = workbook.getSheet(sheetName);
            if (sheet == null) {
                List<String> names = new ArrayList<>();
                workbook.forEach(s -> names.add(s.getSheetName()));
                throw new FrameworkException("Sheet '" + sheetName + "' not found in " + location + ". Available: " + names);
            }
            DataFormatter formatter = new DataFormatter();
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();

            Row header = sheet.getRow(sheet.getFirstRowNum());
            if (header == null) {
                return List.of();
            }
            List<String> columns = new ArrayList<>();
            for (int c = 0; c < header.getLastCellNum(); c++) {
                columns.add(formatter.formatCellValue(header.getCell(c)).trim());
            }

            List<Map<String, String>> rows = new ArrayList<>();
            for (int r = sheet.getFirstRowNum() + 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) {
                    continue;
                }
                Map<String, String> values = new LinkedHashMap<>();
                boolean hasData = false;
                for (int c = 0; c < columns.size(); c++) {
                    if (columns.get(c).isEmpty()) {
                        continue;
                    }
                    Cell cell = row.getCell(c, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                    String value = cell == null ? "" : formatter.formatCellValue(cell, evaluator).trim();
                    hasData |= !value.isEmpty();
                    values.put(columns.get(c), value);
                }
                if (hasData) {
                    rows.add(Collections.unmodifiableMap(values));
                }
            }
            LOG.debug("Read {} row(s) from {}#{}", rows.size(), location, sheetName);
            return Collections.unmodifiableList(rows);
        } catch (IOException e) {
            throw new FrameworkException("Cannot read Excel file " + location, e);
        }
    }
}
