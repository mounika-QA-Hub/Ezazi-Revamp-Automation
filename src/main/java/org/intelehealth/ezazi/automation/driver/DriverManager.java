package org.intelehealth.ezazi.automation.driver;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.intelehealth.ezazi.automation.exceptions.FrameworkException;

import io.appium.java_client.android.AndroidDriver;

/**
 * Holds the {@link AndroidDriver} (and the device it runs on) for the current thread.
 * <p>
 * {@link ThreadLocal} storage lets several tests run in parallel, each with its own session,
 * without passing the driver around. Always call {@link #quitDriver()} in a {@code finally}/
 * {@code alwaysRun} block so sessions and ThreadLocals never leak.
 */
public final class DriverManager {

    private static final Logger LOG = LogManager.getLogger(DriverManager.class);
    private static final ThreadLocal<AndroidDriver> DRIVER = new ThreadLocal<>();
    private static final ThreadLocal<DeviceConfig> DEVICE = new ThreadLocal<>();

    private DriverManager() {
    }

    /**
     * @return the driver bound to the current thread
     * @throws FrameworkException if no session was started on this thread
     */
    public static AndroidDriver getDriver() {
        AndroidDriver driver = DRIVER.get();
        if (driver == null) {
            throw new FrameworkException("No Appium session on thread '" + Thread.currentThread().getName()
                    + "'. Does your test class extend BaseTest? Did session creation fail earlier (see logs)?");
        }
        return driver;
    }

    /** @return true when the current thread owns a session */
    public static boolean hasDriver() {
        return DRIVER.get() != null;
    }

    /**
     * Binds a driver and its device to the current thread.
     *
     * @param driver driver instance
     * @param device device the session runs on
     */
    public static void setDriver(AndroidDriver driver, DeviceConfig device) {
        DRIVER.set(driver);
        DEVICE.set(device);
    }

    /** @return device of the current thread's session, or {@code null} */
    public static DeviceConfig getDevice() {
        return DEVICE.get();
    }

    /** @return adb serial of the current session's device ("" when unknown) */
    public static String getUdid() {
        DeviceConfig device = DEVICE.get();
        if (device != null && device.udid() != null && !device.udid().isBlank()) {
            return device.udid();
        }
        if (DRIVER.get() != null) {
            Object udid = DRIVER.get().getCapabilities().getCapability("appium:udid");
            if (udid == null) {
                udid = DRIVER.get().getCapabilities().getCapability("udid");
            }
            return udid == null ? "" : udid.toString();
        }
        return "";
    }

    /**
     * Quits the session (errors are logged, never thrown) and clears the ThreadLocals.
     *
     * @return the device the session ran on (so the caller can release it), or {@code null}
     */
    public static DeviceConfig quitDriver() {
        AndroidDriver driver = DRIVER.get();
        DeviceConfig device = DEVICE.get();
        try {
            if (driver != null) {
                LOG.info("Quitting session {} on {}", driver.getSessionId(), device);
                driver.quit();
            }
        } catch (RuntimeException e) {
            LOG.warn("Session quit failed (already closed?): {}", e.getMessage());
        } finally {
            DRIVER.remove();
            DEVICE.remove();
        }
        return device;
    }
}
