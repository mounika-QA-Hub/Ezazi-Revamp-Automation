package org.intelehealth.ezazi.automation.driver;

import java.net.URL;
import java.time.Duration;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.intelehealth.ezazi.automation.config.ConfigKeys;
import org.intelehealth.ezazi.automation.config.ConfigReader;
import org.intelehealth.ezazi.automation.exceptions.FrameworkException;
import org.openqa.selenium.SessionNotCreatedException;
import org.openqa.selenium.WebDriverException;

import io.appium.java_client.android.AndroidDriver;
import io.appium.java_client.android.options.UiAutomator2Options;

/**
 * Factory that turns a {@link DeviceConfig} into a live {@link AndroidDriver}.
 * <p>
 * Session creation is the flakiest step in mobile automation (UiAutomator2 server install, adb
 * hiccups), so it is retried {@code driver.session.create.retries} times before giving up with a
 * message that points at the troubleshooting guide. The factory is the only class that knows
 * about capabilities; adding iOS later means adding a sibling factory, not changing tests.
 */
public final class DriverFactory {

    private static final Logger LOG = LogManager.getLogger(DriverFactory.class);

    private DriverFactory() {
    }

    /**
     * Creates a new Android session.
     *
     * @param device    target device
     * @param serverUrl Appium server URL
     * @return ready-to-use driver (implicit wait = 0; use explicit waits only)
     */
    public static AndroidDriver createAndroidDriver(DeviceConfig device, URL serverUrl) {
        UiAutomator2Options options = UiAutomator2OptionsFactory.build(device);
        int maxAttempts = 1 + Math.max(0, ConfigReader.getInt(ConfigKeys.SESSION_CREATE_RETRIES, 1));

        for (int attempt = 1; ; attempt++) {
            long start = System.currentTimeMillis();
            try {
                LOG.info("Creating Appium session on {} (attempt {}/{})", device, attempt, maxAttempts);
                AndroidDriver driver = new AndroidDriver(serverUrl, options);
                driver.manage().timeouts().implicitlyWait(Duration.ZERO);
                LOG.info("Session {} created in {} ms", driver.getSessionId(), System.currentTimeMillis() - start);
                return driver;
            } catch (SessionNotCreatedException e) {
                if (attempt >= maxAttempts) {
                    throw new FrameworkException("Could not create an Appium session on " + device + " after "
                            + maxAttempts + " attempt(s). Check target/logs/appium-server.log and the README "
                            + "troubleshooting section. Root cause: " + firstLine(e), e);
                }
                LOG.warn("Session creation failed, retrying: {}", firstLine(e));
            } catch (WebDriverException e) {
                throw new FrameworkException("Appium server at " + serverUrl + " rejected the session request: "
                        + firstLine(e), e);
            }
        }
    }

    private static String firstLine(Throwable t) {
        String message = String.valueOf(t.getMessage());
        int newline = message.indexOf('\n');
        return newline > 0 ? message.substring(0, newline) : message;
    }
}
