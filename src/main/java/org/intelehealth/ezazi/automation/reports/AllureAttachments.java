package org.intelehealth.ezazi.automation.reports;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.intelehealth.ezazi.automation.config.ConfigKeys;
import org.intelehealth.ezazi.automation.config.ConfigReader;
import org.intelehealth.ezazi.automation.driver.DriverManager;
import org.intelehealth.ezazi.automation.utils.AdbUtils;
import org.intelehealth.ezazi.automation.utils.ScreenshotUtils;

import io.qameta.allure.Allure;

/**
 * Attachment helpers. Every method is null-safe and never throws: a failing attachment must
 * never hide the real test failure.
 */
public final class AllureAttachments {

    private static final Logger LOG = LogManager.getLogger(AllureAttachments.class);

    private AllureAttachments() {
    }

    /**
     * Attaches a PNG of the current screen.
     *
     * @param name attachment title
     */
    public static void screenshot(String name) {
        byte[] png = ScreenshotUtils.capture();
        if (png.length > 0) {
            Allure.addAttachment(name, "image/png", new ByteArrayInputStream(png), "png");
        }
    }

    /** Attaches the current UI hierarchy (XML) - the fastest way to fix a broken locator. */
    public static void pageSource() {
        if (!DriverManager.hasDriver()) {
            return;
        }
        try {
            text("Page source", DriverManager.getDriver().getPageSource(), "text/xml", "xml");
        } catch (RuntimeException e) {
            LOG.warn("Page source not available: {}", e.getMessage());
        }
    }

    /** Attaches the last N logcat lines of the app process ({@code report.logcat.lines}). */
    public static void deviceLogs() {
        try {
            String udid = DriverManager.getUdid();
            String pkg = ConfigReader.get(ConfigKeys.APP_PACKAGE, "");
            String logs = AdbUtils.logcat(udid, pkg, ConfigReader.getInt(ConfigKeys.REPORT_LOGCAT_LINES, 300));
            text("Device log (logcat)", logs, "text/plain", "txt");
        } catch (RuntimeException e) {
            LOG.warn("Device logs not available: {}", e.getMessage());
        }
    }

    /**
     * Attaches arbitrary text.
     *
     * @param name      title
     * @param content   text
     * @param mimeType  e.g. text/plain
     * @param extension e.g. txt
     */
    public static void text(String name, String content, String mimeType, String extension) {
        if (content == null || content.isEmpty()) {
            return;
        }
        Allure.addAttachment(name, mimeType, new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)), extension);
    }

    /** Attaches screenshot + page source + device logs (used on failure). */
    public static void failureArtifacts() {
        screenshot("Screenshot on failure");
        pageSource();
        deviceLogs();
    }
}
