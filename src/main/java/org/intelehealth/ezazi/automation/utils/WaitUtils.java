package org.intelehealth.ezazi.automation.utils;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

import org.intelehealth.ezazi.automation.config.ConfigKeys;
import org.intelehealth.ezazi.automation.config.ConfigReader;
import org.intelehealth.ezazi.automation.driver.DriverManager;
import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.FluentWait;
import org.openqa.selenium.support.ui.Wait;

import io.appium.java_client.android.AndroidDriver;

/**
 * Reusable explicit waits. This is the ONLY place that waits for the UI - never use
 * {@code Thread.sleep} or implicit waits.
 * <p>
 * Every wait polls quickly ({@code timeout.polling.millis}, default 250 ms), ignores transient
 * stale/missing-element errors that are normal while a React Native screen re-renders, and fails
 * with a message naming the locator and timeout.
 */
public final class WaitUtils {

    private WaitUtils() {
    }

    /** Default explicit timeout ({@code timeout.explicit.seconds}). */
    public static Duration defaultTimeout() {
        return ConfigReader.getSeconds(ConfigKeys.TIMEOUT_EXPLICIT, 15);
    }

    /** Short timeout for optional elements such as pop-ups ({@code timeout.short.seconds}). */
    public static Duration shortTimeout() {
        return ConfigReader.getSeconds(ConfigKeys.TIMEOUT_SHORT, 3);
    }

    /** Timeout for app (re)launch ({@code timeout.app.launch.seconds}). */
    public static Duration appLaunchTimeout() {
        return ConfigReader.getSeconds(ConfigKeys.TIMEOUT_APP_LAUNCH, 60);
    }

    /**
     * Creates a fluent wait for the current driver.
     *
     * @param timeout maximum wait
     * @return configured wait
     */
    public static Wait<AndroidDriver> fluentWait(Duration timeout) {
        return new FluentWait<>(DriverManager.getDriver())
                .withTimeout(timeout)
                .pollingEvery(Duration.ofMillis(ConfigReader.getInt(ConfigKeys.TIMEOUT_POLLING_MILLIS, 250)))
                .ignoring(NoSuchElementException.class)
                .ignoring(StaleElementReferenceException.class);
    }

    /**
     * Generic wait for any condition.
     *
     * @param condition     function returning a non-null/true value when satisfied
     * @param timeout       maximum wait
     * @param failureReason text used in the timeout message
     * @param <T>           condition result type
     * @return condition result
     */
    public static <T> T until(Function<AndroidDriver, T> condition, Duration timeout, String failureReason) {
        try {
            return fluentWait(timeout).until(condition);
        } catch (TimeoutException e) {
            throw new TimeoutException(failureReason + " (waited " + timeout.toSeconds() + "s)", e);
        }
    }

    /** Waits for an element to be visible using the default timeout. */
    public static WebElement waitForVisible(By locator) {
        return waitForVisible(locator, defaultTimeout());
    }

    /** Waits for an element to be visible. */
    public static WebElement waitForVisible(By locator, Duration timeout) {
        return until(d -> {
            WebElement element = d.findElement(locator);
            return element.isDisplayed() ? element : null;
        }, timeout, "Element not visible: " + locator);
    }

    /** Waits for an element to be visible and enabled (clickable). */
    public static WebElement waitForClickable(By locator) {
        return waitForClickable(locator, defaultTimeout());
    }

    /** Waits for an element to be visible and enabled (clickable). */
    public static WebElement waitForClickable(By locator, Duration timeout) {
        return until(d -> {
            WebElement element = d.findElement(locator);
            return element.isDisplayed() && element.isEnabled() ? element : null;
        }, timeout, "Element not clickable: " + locator);
    }

    /** Waits until an element exists in the hierarchy (visible or not). */
    public static WebElement waitForPresence(By locator, Duration timeout) {
        return until(d -> d.findElement(locator), timeout, "Element not present: " + locator);
    }

    /**
     * Waits until the element is gone or hidden.
     *
     * @return true if it disappeared in time, false otherwise (never throws)
     */
    public static boolean waitForInvisibility(By locator, Duration timeout) {
        try {
            return fluentWait(timeout).until(ExpectedConditions.invisibilityOfElementLocated(locator));
        } catch (TimeoutException e) {
            return false;
        }
    }

    /** Waits for the element's text to equal {@code expected}. */
    public static WebElement waitForText(By locator, String expected, Duration timeout) {
        return until(d -> {
            WebElement element = d.findElement(locator);
            return expected.equals(element.getText()) ? element : null;
        }, timeout, "Text '" + expected + "' not shown in " + locator);
    }

    /** Waits for an attribute to have a value, e.g. {@code checked=true}. */
    public static WebElement waitForAttribute(By locator, String attribute, String expected, Duration timeout) {
        return until(d -> {
            WebElement element = d.findElement(locator);
            return expected.equals(element.getAttribute(attribute)) ? element : null;
        }, timeout, "Attribute " + attribute + "='" + expected + "' not reached for " + locator);
    }

    /**
     * Non-throwing visibility check: perfect for optional pop-ups and assertions.
     *
     * @return true if visible within the timeout
     */
    public static boolean isVisible(By locator, Duration timeout) {
        try {
            waitForVisible(locator, timeout);
            return true;
        } catch (TimeoutException e) {
            return false;
        }
    }

    /**
     * Instant (no wait) visibility check.
     *
     * @return true if the element is currently displayed
     */
    public static boolean isVisibleNow(By locator) {
        List<WebElement> elements = DriverManager.getDriver().findElements(locator);
        try {
            return !elements.isEmpty() && elements.get(0).isDisplayed();
        } catch (StaleElementReferenceException e) {
            return false;
        }
    }

    /**
     * Waits until ANY of the locators is visible - useful when a screen can show one of several
     * states (e.g. permission dialog or login screen).
     *
     * @return the locator that became visible first
     */
    public static By waitForAnyVisible(Duration timeout, By... locators) {
        return until(d -> Arrays.stream(locators).filter(WaitUtils::isVisibleNow).findFirst().orElse(null),
                timeout, "None of the elements became visible: " + Arrays.toString(locators));
    }
}
