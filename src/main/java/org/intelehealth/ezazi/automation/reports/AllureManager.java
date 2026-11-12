package org.intelehealth.ezazi.automation.reports;

import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.intelehealth.ezazi.automation.config.ConfigKeys;
import org.intelehealth.ezazi.automation.config.ConfigReader;
import org.intelehealth.ezazi.automation.utils.AdbUtils;

/**
 * Manages the Allure results folder: cleaning before a run, writing
 * {@code environment.properties} and {@code categories.json}, and (optionally) generating the
 * HTML report with the Allure CLI after the run.
 */
public final class AllureManager {

    private static final Logger LOG = LogManager.getLogger(AllureManager.class);
    private static final String CATEGORIES_RESOURCE = "allure/categories.json";

    private AllureManager() {
    }

    /** @return results directory (system property set by surefire, else config, else default) */
    public static Path resultsDir() {
        String dir = System.getProperty(ConfigKeys.ALLURE_RESULTS_DIR);
        if (dir == null || dir.isBlank()) {
            dir = ConfigReader.get(ConfigKeys.ALLURE_RESULTS_DIR, "target/allure-results");
        }
        return Path.of(dir);
    }

    /** Deletes old results so the report only shows this run (keeps the history folder). */
    public static void cleanResults() {
        Path dir = resultsDir();
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (Stream<Path> files = Files.walk(dir)) {
            files.sorted(Comparator.reverseOrder())
                    .filter(p -> !p.equals(dir) && !p.startsWith(dir.resolve("history")))
                    .forEach(p -> p.toFile().delete());
            LOG.info("Cleaned Allure results in {}", dir.toAbsolutePath());
        } catch (IOException e) {
            LOG.warn("Could not clean {}: {}", dir, e.getMessage());
        }
    }

    /**
     * Writes the "Environment" widget data.
     *
     * @param values key/value pairs
     */
    public static void writeEnvironment(Map<String, String> values) {
        Path file = resultsDir().resolve("environment.properties");
        try {
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                for (Map.Entry<String, String> e : values.entrySet()) {
                    writer.write(escape(e.getKey()).replace(" ", "\\ ") + "=" + escape(e.getValue()) + System.lineSeparator());
                }
            }
        } catch (IOException e) {
            LOG.warn("Could not write {}: {}", file, e.getMessage());
        }
    }

    /** Copies {@code src/test/resources/allure/categories.json} into the results folder. */
    public static void copyCategories() {
        try (InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(CATEGORIES_RESOURCE)) {
            if (in == null) {
                LOG.warn("{} not found on classpath; failure categories will use Allure defaults", CATEGORIES_RESOURCE);
                return;
            }
            Files.createDirectories(resultsDir());
            Files.copy(in, resultsDir().resolve("categories.json"), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            LOG.warn("Could not copy categories.json: {}", e.getMessage());
        }
    }

    /**
     * Runs {@code allure generate} when {@code allure.report.generate=true} and the Allure CLI is
     * on the PATH. Otherwise logs the command to run manually.
     */
    public static void generateReportIfEnabled() {
        String reportDir = ConfigReader.get(ConfigKeys.ALLURE_REPORT_DIR, "target/allure-report");
        if (!ConfigReader.getBoolean(ConfigKeys.ALLURE_REPORT_GENERATE, false)) {
            LOG.info("View the report with: allure serve {}   (or mvn allure:serve)", resultsDir());
            return;
        }
        boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
        List<String> command = new java.util.ArrayList<>(windows ? List.of("cmd", "/c", "allure") : List.of("allure"));
        command.addAll(List.of("generate", resultsDir().toString(), "-o", reportDir, "--clean"));
        try {
            AdbUtils.CommandResult result = AdbUtils.execute(command, Duration.ofMinutes(3));
            if (result.isSuccess()) {
                LOG.info("Allure report generated: {}/index.html", Path.of(reportDir).toAbsolutePath());
            } else {
                LOG.warn("allure generate failed: {}", result.output());
            }
        } catch (RuntimeException e) {
            LOG.warn("Allure CLI not found on PATH ({}). Use 'mvn allure:report' instead.", e.getMessage());
        }
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\n", " ");
    }
}
