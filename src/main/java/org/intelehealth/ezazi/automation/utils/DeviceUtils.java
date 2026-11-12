package org.intelehealth.ezazi.automation.utils;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.intelehealth.ezazi.automation.driver.DriverManager;
import org.openqa.selenium.ScreenOrientation;

import io.appium.java_client.android.AndroidDriver;
import io.qameta.allure.Step;

/**
 * Device-level controls: keyboard, orientation, network and clock.
 * <p>
 * Network and clock changes use host adb shell commands that work on emulators and real devices
 * without root ({@code cmd connectivity}, {@code svc}, {@code cmd alarm}). Always restore the
 * automatic clock in an {@code @AfterMethod(alwaysRun = true)} after date-boundary tests.
 */
public final class DeviceUtils {

    private static final Logger LOG = LogManager.getLogger(DeviceUtils.class);

    private DeviceUtils() {
    }

    private static AndroidDriver driver() {
        return DriverManager.getDriver();
    }

    private static String udid() {
        return DriverManager.getUdid();
    }

    // ----------------------------------------------------------------------------- keyboard

    /** Hides the soft keyboard only when it is shown (avoids an exception). */
    public static void hideKeyboard() {
        if (driver().isKeyboardShown()) {
            driver().hideKeyboard();
        }
    }

    // -------------------------------------------------------------------------- orientation

    /**
     * Rotates the device.
     *
     * @param orientation PORTRAIT or LANDSCAPE
     */
    @Step("Rotate device to {orientation}")
    public static void setOrientation(ScreenOrientation orientation) {
        if (driver().getOrientation() != orientation) {
            driver().rotate(orientation);
        }
    }

    /** @return current orientation */
    public static ScreenOrientation getOrientation() {
        return driver().getOrientation();
    }

    // ------------------------------------------------------------------------------ network

    /**
     * Turns airplane mode on/off (Android 11+).
     *
     * @param enabled true = offline
     */
    @Step("Set airplane mode: {enabled}")
    public static void setAirplaneMode(boolean enabled) {
        AdbUtils.shell(udid(), "cmd", "connectivity", "airplane-mode", enabled ? "enable" : "disable");
        LOG.info("Airplane mode {}", enabled ? "ON" : "OFF");
    }

    /** Turns Wi-Fi on/off. */
    @Step("Set Wi-Fi: {enabled}")
    public static void setWifi(boolean enabled) {
        AdbUtils.shell(udid(), "svc", "wifi", enabled ? "enable" : "disable");
    }

    /** Turns mobile data on/off. */
    @Step("Set mobile data: {enabled}")
    public static void setMobileData(boolean enabled) {
        AdbUtils.shell(udid(), "svc", "data", enabled ? "enable" : "disable");
    }

    // -------------------------------------------------------------------------------- clock

    /**
     * Sets the device clock (disables automatic time first). Use for date-boundary tests such as
     * "admission just before midnight".
     *
     * @param dateTime target instant (its zone is respected)
     */
    @Step("Set device date/time to {dateTime}")
    public static void setDateTime(ZonedDateTime dateTime) {
        AdbUtils.shell(udid(), "settings", "put", "global", "auto_time", "0");
        AdbUtils.shell(udid(), "cmd", "alarm", "set-time", String.valueOf(dateTime.toInstant().toEpochMilli()));
        LOG.info("Device clock set to {}", dateTime);
    }

    /**
     * Sets the device time zone (disables automatic time zone first).
     *
     * @param zone Olson id, e.g. {@code Asia/Kolkata}
     */
    @Step("Set device time zone to {zone}")
    public static void setTimeZone(ZoneId zone) {
        AdbUtils.shell(udid(), "settings", "put", "global", "auto_time_zone", "0");
        AdbUtils.shell(udid(), "cmd", "alarm", "set-timezone", zone.getId());
        LOG.info("Device time zone set to {}", zone);
    }

    /** Re-enables network-provided time and time zone. */
    @Step("Restore automatic date, time and time zone")
    public static void restoreAutomaticDateTime() {
        AdbUtils.shell(udid(), "settings", "put", "global", "auto_time", "1");
        AdbUtils.shell(udid(), "settings", "put", "global", "auto_time_zone", "1");
    }

    /** @return the device's current time zone */
    public static ZoneId getTimeZone() {
        String tz = AdbUtils.getProp(udid(), "persist.sys.timezone");
        return tz.isBlank() ? ZoneId.systemDefault() : ZoneId.of(tz);
    }

    /** @return the device's current time in its own zone */
    public static ZonedDateTime getDateTime() {
        long epochSeconds = Long.parseLong(AdbUtils.shell(udid(), "date", "+%s").trim());
        return Instant.ofEpochSecond(epochSeconds).atZone(getTimeZone());
    }

    // ------------------------------------------------------------------------------ device info

    /** @return e.g. "Pixel Tablet / Android 15 (API 35)" */
    public static String describe(String serial) {
        return AdbUtils.getProp(serial, "ro.product.model") + " / Android "
                + AdbUtils.getProp(serial, "ro.build.version.release") + " (API "
                + AdbUtils.getProp(serial, "ro.build.version.sdk") + ")";
    }

    /**
     * @param serial     device serial
     * @param appPackage application id
     * @return installed versionName or "unknown"
     */
    public static String appVersion(String serial, String appPackage) {
        try {
            String dump = AdbUtils.shell(serial, "dumpsys", "package", appPackage);
            for (String line : dump.split("\\R")) {
                String trimmed = line.trim();
                if (trimmed.startsWith("versionName=")) {
                    return trimmed.substring("versionName=".length());
                }
            }
        } catch (RuntimeException e) {
            LOG.debug("Could not read app version: {}", e.getMessage());
        }
        return "unknown";
    }
}
