package org.intelehealth.ezazi.automation.utils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.intelehealth.ezazi.automation.driver.DriverManager;
import org.openqa.selenium.OutputType;

/**
 * Screenshot helpers. Screenshots are kept as bytes (fast, no temp files) and only written to
 * disk when explicitly requested.
 */
public final class ScreenshotUtils {

    private static final Logger LOG = LogManager.getLogger(ScreenshotUtils.class);
    private static final Path SCREENSHOT_DIR = Path.of("target", "screenshots");

    private ScreenshotUtils() {
    }

    /**
     * @return PNG bytes of the current screen, or an empty array when no session is available
     */
    public static byte[] capture() {
        if (!DriverManager.hasDriver()) {
            return new byte[0];
        }
        try {
            return DriverManager.getDriver().getScreenshotAs(OutputType.BYTES);
        } catch (RuntimeException e) {
            LOG.warn("Screenshot failed: {}", e.getMessage());
            return new byte[0];
        }
    }

    /**
     * Captures and saves a screenshot to {@code target/screenshots}.
     *
     * @param name file name prefix (sanitised)
     * @return the saved file, or {@code null} if nothing could be captured
     */
    public static Path captureToFile(String name) {
        byte[] png = capture();
        if (png.length == 0) {
            return null;
        }
        try {
            Files.createDirectories(SCREENSHOT_DIR);
            Path file = SCREENSHOT_DIR.resolve(sanitize(name) + "_" + DateTimeUtils.fileTimestamp() + ".png");
            Files.write(file, png);
            return file;
        } catch (IOException e) {
            LOG.warn("Could not save screenshot: {}", e.getMessage());
            return null;
        }
    }

    private static String sanitize(String name) {
        return name.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
